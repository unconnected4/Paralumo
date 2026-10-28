<#
.SYNOPSIS
Runs coding agents on one task: implementers in turn, then a reviewer, until the task is done.

.DESCRIPTION
Design and rules: docs/orchestration.md. The pipeline has two phases.

Implementation: the implementers listed in roles.json run one after another, cycling
through the list. Each one reports status in the handoff front matter. When every
implementer in a row reports ready-for-review (nothing left to do), or the per-cycle
round cap is hit, the reviewer runs. An implementer that sets blocked sends the task to
the reviewer at once.

Review: the reviewer resolves open questions where it can, checks the work and sets
status: done (task closed), in-progress (findings recorded, implementers start a new
cycle) or blocked (Alex is needed, loop stops).

Every round must end with a clean working tree, a new commit and an updated handoff
file; otherwise the loop stops with the reason.

.PARAMETER Task
Handoff slug: docs/handoff/active/<Task>.md must exist.

.PARAMETER Config
Path to the role table. Default: roles.json next to this script.

.PARAMETER StartWith
Role to run first: one of the implementers (cycle starts there) or the reviewer.
Default: the first implementer.

.PARAMETER MaxRounds
Overrides limits.maxRounds from the config.

.PARAMETER DryRun
Render the first prompt and print the command without running anything.

.EXAMPLE
powershell -ExecutionPolicy Bypass -File scripts/orchestrate/orchestrate.ps1 -Task step1-backend
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Task,
    [string]$Config = '',
    [string]$StartWith = '',
    [int]$MaxRounds = 0,
    [switch]$DryRun
)

Set-StrictMode -Version 2.0
$ErrorActionPreference = 'Stop'

# $PSScriptRoot is not yet set while parameter defaults are evaluated, so resolve here.
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not $Config) { $Config = Join-Path $scriptDir 'roles.json' }

# ---------------------------------------------------------------- helpers

function Invoke-Git {
    param([Parameter(Mandatory = $true)][string[]]$GitArgs)
    $out = & git @GitArgs
    if ($LASTEXITCODE -ne 0) { throw "git $($GitArgs -join ' ') failed (exit $LASTEXITCODE)" }
    return $out
}

function Test-GitAncestor {
    # $true when $Ancestor is $Commit or one of its ancestors.
    param([string]$Ancestor, [string]$Commit)
    & git merge-base --is-ancestor $Ancestor $Commit 2>$null
    return ($LASTEXITCODE -eq 0)
}

function ConvertTo-ArgumentString {
    # Windows CommandLineToArgvW quoting. PowerShell 5.1's Start-Process joins an
    # -ArgumentList array with spaces and no quoting, so we build the string ourselves.
    param([string[]]$Items)
    $quoted = foreach ($item in $Items) {
        if ($item -match '[\s"]' -or $item -eq '') {
            '"' + ($item -replace '(\\*)"', '$1$1\"') + '"'
        } else {
            $item
        }
    }
    return ($quoted -join ' ')
}

function Read-FrontMatter {
    # Returns a hashtable of the YAML-ish front matter scalars (key: value) of a handoff file.
    param([string]$Path)
    $result = @{}
    $lines = Get-Content -LiteralPath $Path
    if ($lines.Count -eq 0 -or $lines[0].Trim() -ne '---') { return $result }
    for ($i = 1; $i -lt $lines.Count; $i++) {
        $line = $lines[$i]
        if ($line.Trim() -eq '---') { break }
        if ($line -match '^([A-Za-z_]+):\s*(.*?)\s*(#.*)?$') {
            $result[$Matches[1]] = $Matches[2].Trim()
        }
    }
    return $result
}

function Get-OpenQuestions {
    # Open "For Alex:" lines of a handoff file: tagged, not struck through, not yet resolved.
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) { return @() }
    return @(Get-Content -LiteralPath $Path | ForEach-Object { $_.Trim() } | Where-Object {
        $_ -match '^-\s*For Alex:' -and $_ -notmatch '\(resolved by ' -and $_ -notmatch '\(by Alex'
    } | ForEach-Object { $_ -replace '^-\s*', '' })
}

function Write-Summary {
    param([string]$Text)
    Write-Host $Text
    Add-Content -LiteralPath $script:summaryPath -Value $Text -Encoding UTF8
}

# ---------------------------------------------------------------- setup

$repoRoot = (Invoke-Git @('rev-parse', '--show-toplevel')) | Select-Object -First 1
$repoRoot = (Resolve-Path -LiteralPath $repoRoot).Path
Set-Location -LiteralPath $repoRoot

if (-not (Test-Path -LiteralPath $Config)) { throw "Config not found: $Config" }
$cfg = Get-Content -LiteralPath $Config -Raw | ConvertFrom-Json

$handoffRel = "docs/handoff/active/$Task.md"
$handoffPath = Join-Path $repoRoot $handoffRel
if (-not (Test-Path -LiteralPath $handoffPath)) {
    throw "Handoff file not found: $handoffRel. Create it from docs/handoff/TEMPLATE.md with Goal and Next step filled in."
}

$dirty = Invoke-Git @('status', '--porcelain')
if ($dirty) { throw "Working tree is not clean. Commit or stash first:`n$($dirty -join "`n")" }

$implementers = @($cfg.pipeline.implementers | ForEach-Object { [string]$_ })
$reviewer = [string]$cfg.pipeline.reviewer
if ($implementers.Count -eq 0) { throw "pipeline.implementers is empty in $Config" }
if (-not $reviewer) { throw "pipeline.reviewer is missing in $Config" }

$roleNames = @($cfg.roles.PSObject.Properties.Name)
foreach ($name in ($implementers + $reviewer)) {
    if ($roleNames -notcontains $name) { throw "Role '$name' is used in pipeline but not defined in roles. Roles: $($roleNames -join ', ')" }
}

if ($MaxRounds -le 0) { $MaxRounds = [int]$cfg.limits.maxRounds }
$maxImplRounds = [int]$cfg.limits.maxImplementationRoundsPerCycle
$timeoutMs = [int]$cfg.limits.roundTimeoutMinutes * 60 * 1000

$phase = 'implement'
$implIndex = 0
if ($StartWith) {
    if ($StartWith -eq $reviewer) { $phase = 'review' }
    elseif ($implementers -contains $StartWith) { $implIndex = [array]::IndexOf($implementers, $StartWith) }
    else { throw "Unknown -StartWith '$StartWith'. Implementers: $($implementers -join ', '); reviewer: $reviewer" }
}

$runStamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$runDir = Join-Path $repoRoot "AgentsOutput/orchestrator/$runStamp-$Task"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$script:summaryPath = Join-Path $runDir 'summary.md'

$commonTemplate = Get-Content -LiteralPath (Join-Path $scriptDir 'prompts/_common.md') -Raw
$implementerLabels = ($implementers | ForEach-Object { "$_ ($($cfg.roles.$_.agent))" }) -join ', '
$loopStart = (Invoke-Git @('rev-parse', 'HEAD')) | Select-Object -First 1

Write-Summary "# Orchestrator run $runStamp"
Write-Summary "Task: $Task | start: $loopStart | config: $Config | max rounds: $MaxRounds"
Write-Summary "Implementers: $implementerLabels | reviewer: $reviewer ($($cfg.roles.$reviewer.agent))"
Write-Summary ''

# ---------------------------------------------------------------- loop

$round = 0
$cycle = 1                 # review cycle: implementation phase + the review that ends it
$nothingStreak = 0         # consecutive implementers that reported nothing left to do
$implRoundsThisCycle = 0
$notes = 'none'
$newQuestions = New-Object System.Collections.Generic.List[string]
$stopReason = $null
$exitCode = 1

while ($round -lt $MaxRounds) {
    $round++
    $role = if ($phase -eq 'review') { $reviewer } else { $implementers[$implIndex] }
    $roleCfg = $cfg.roles.$role
    $agent = [string]$roleCfg.agent
    $roundTag = ('round-{0:d2}-{1}' -f $round, $role)
    $startHead = (Invoke-Git @('rev-parse', 'HEAD')) | Select-Object -First 1

    # Model: each CLI spells its flag differently, so roles.json puts the flag in args and
    # the driver only fills the {model} placeholder from the role's "model" field.
    $model = ''
    if ($roleCfg.PSObject.Properties.Name -contains 'model') { $model = [string]$roleCfg.model }
    $wantsModel = @($roleCfg.args | Where-Object { ([string]$_).Contains('{model}') }).Count -gt 0
    if ($wantsModel -and -not $model) { throw "Role '$role' uses {model} in args but has no 'model' field in $Config" }

    # Render the prompt to a file; the agent gets a one-line pointer to it.
    $roleTemplate = Get-Content -LiteralPath (Join-Path $repoRoot $roleCfg.prompt) -Raw
    $prompt = $roleTemplate.Replace('{common}', $commonTemplate.TrimEnd())
    $prompt = $prompt.Replace('{agent}', $agent).Replace('{task}', $Task).Replace('{handoff}', $handoffRel)
    $prompt = $prompt.Replace('{round}', [string]$round).Replace('{cycle}', [string]$cycle)
    $prompt = $prompt.Replace('{implementers}', $implementerLabels).Replace('{notes}', $notes)
    $promptPath = Join-Path $runDir "$roundTag.prompt.md"
    [System.IO.File]::WriteAllText($promptPath, $prompt, (New-Object System.Text.UTF8Encoding($false)))

    $pointer = "Read the file $promptPath and follow the instructions in it exactly."
    $argItems = @($roleCfg.args | ForEach-Object { ([string]$_).Replace('{prompt}', $pointer).Replace('{model}', $model) })
    $argString = ConvertTo-ArgumentString $argItems
    $exe = (Get-Command $roleCfg.command -CommandType Application -ErrorAction Stop | Select-Object -First 1).Source

    $modelLabel = if ($model) { $model } else { 'CLI default' }
    Write-Summary "## Round $round : $phase / $role ($agent, model: $modelLabel), cycle $cycle"
    Write-Summary "Start: $startHead"
    Write-Summary "Command: $exe $argString"

    if ($DryRun) {
        Write-Summary "Dry run: prompt rendered to $promptPath, nothing executed."
        $stopReason = 'dry run'
        $exitCode = 0
        break
    }

    $stdoutPath = Join-Path $runDir "$roundTag.stdout.log"
    $stderrPath = Join-Path $runDir "$roundTag.stderr.log"
    $started = Get-Date
    $proc = Start-Process -FilePath $exe -ArgumentList $argString -WorkingDirectory $repoRoot `
        -NoNewWindow -PassThru -RedirectStandardOutput $stdoutPath -RedirectStandardError $stderrPath
    $null = $proc.Handle   # PowerShell 5.1: ExitCode stays empty unless the handle is read before the wait
    $finished = $proc.WaitForExit($timeoutMs)
    if (-not $finished) {
        & taskkill /PID $proc.Id /T /F 2>$null | Out-Null
        try { $proc.Kill() } catch { }
        $stopReason = "round $round ($role) exceeded the timeout of $($cfg.limits.roundTimeoutMinutes) minutes and was killed"
        break
    }
    $elapsed = [int]((Get-Date) - $started).TotalMinutes
    Write-Summary "Exit code: $($proc.ExitCode) after $elapsed min. Logs: $stdoutPath"

    # ---- protocol checks, same for every role
    if ($proc.ExitCode -ne 0) {
        $stopReason = "round $round ($role) exited with code $($proc.ExitCode); see $stderrPath"
        break
    }
    $dirty = Invoke-Git @('status', '--porcelain')
    if ($dirty) {
        $stopReason = "round $round ($role) left uncommitted changes:`n$($dirty -join "`n")"
        break
    }
    $endHead = (Invoke-Git @('rev-parse', 'HEAD')) | Select-Object -First 1
    if ($endHead -eq $startHead) {
        $stopReason = "round $round ($role) made no commit"
        break
    }
    $changed = @(Invoke-Git @('diff', '--name-only', $startHead, $endHead))
    Write-Summary ("Commits: {0}..{1} ({2} files)" -f $startHead.Substring(0, 7), $endHead.Substring(0, 7), $changed.Count)

    $handoffTouched = @($changed | Where-Object { $_ -eq $handoffRel -or $_ -like 'docs/handoff/archive/*' }).Count -gt 0
    if (-not $handoffTouched) {
        $stopReason = "round $round ($role) committed without updating $handoffRel"
        break
    }

    $diffLines = @(& git diff -U0 $startHead $endHead -- docs/handoff/)
    foreach ($line in $diffLines) {
        if ($line -match '^\+.*For Alex:') { $newQuestions.Add(("round {0} ({1}): {2}" -f $round, $role, $line.Substring(1).Trim())) }
    }
    $codeFiles = @($changed | Where-Object { $_ -notlike 'docs/handoff/*' })

    # Archived handoff means the reviewer closed the task.
    if (-not (Test-Path -LiteralPath $handoffPath)) {
        $archived = @($changed | Where-Object { $_ -like 'docs/handoff/archive/*' })
        $stopReason = "task finished and handoff archived to $($archived -join ', ')"
        $exitCode = 0
        break
    }

    $front = Read-FrontMatter $handoffPath
    $status = if ($front.ContainsKey('status')) { $front['status'] } else { '' }
    $notes = 'none'
    if ($front.ContainsKey('last_commit')) {
        & git rev-parse --verify --quiet "$($front['last_commit'])^{commit}" 2>$null | Out-Null
        $resolves = ($LASTEXITCODE -eq 0)
        if (-not $resolves -or -not (Test-GitAncestor $startHead $front['last_commit'])) {
            $notes = "the previous round left last_commit ($($front['last_commit'])) stale in the handoff front matter; set it correctly this time."
            Write-Summary "Warning: $notes"
        }
    }
    Write-Summary "Status: $status | code files changed: $($codeFiles.Count)"

    # ---- routing
    if ($phase -eq 'implement') {
        $implRoundsThisCycle++
        $toReview = $false
        switch ($status) {
            'in-progress' {
                if ($codeFiles.Count -eq 0) {
                    $nothingStreak++
                    Write-Summary "Note: reported in-progress but changed nothing outside docs/handoff/; counted as nothing to do."
                } else {
                    $nothingStreak = 0
                }
            }
            'ready-for-review' { $nothingStreak++ }
            'done' {
                $nothingStreak++
                Write-Summary "Warning: implementer set status done; only the reviewer may. Treated as ready-for-review."
            }
            'blocked' {
                $toReview = $true
                $notes = "implementer $role ($agent) set status blocked; resolve its For Alex: questions if you can, then review."
            }
            default { $stopReason = "unrecognised status '$status' in $handoffRel" }
        }
        if ($stopReason) { break }

        if ($nothingStreak -ge $implementers.Count) {
            $toReview = $true
            $notes = "all $($implementers.Count) implementers in a row reported nothing left to do."
        } elseif (-not $toReview -and $implRoundsThisCycle -ge $maxImplRounds) {
            $toReview = $true
            $notes = "the implementation phase hit its cap of $maxImplRounds rounds without every implementer reporting nothing left; review what exists."
            Write-Summary "Warning: $notes"
        }

        if ($toReview) {
            $phase = 'review'
            Write-Summary "-> review ($notes)"
        } else {
            $implIndex = ($implIndex + 1) % $implementers.Count
        }
    } else {
        switch ($status) {
            'done'        { $stopReason = 'task done (status: done)'; $exitCode = 0 }
            'abandoned'   { $stopReason = 'task abandoned (status: abandoned)'; $exitCode = 0 }
            'blocked'     { $stopReason = 'task blocked; Alex is needed (status: blocked)' }
            'in-progress' {
                $cycle++
                $phase = 'implement'
                $implIndex = 0
                $nothingStreak = 0
                $implRoundsThisCycle = 0
                $notes = "the reviewer recorded findings; this is review cycle $cycle. Findings are at the top of Next step."
                Write-Summary "-> implementation cycle $cycle"
            }
            default       { $stopReason = "unrecognised status '$status' in $handoffRel" }
        }
        if ($stopReason) { break }
    }
    Write-Summary ''
}

if (-not $stopReason) { $stopReason = "round limit of $MaxRounds reached" }

# ---------------------------------------------------------------- report

$finalHead = (Invoke-Git @('rev-parse', 'HEAD')) | Select-Object -First 1
Write-Summary ''
Write-Summary '## Result'
Write-Summary "Stopped after $round round(s), $cycle review cycle(s): $stopReason"
Write-Summary "Commits this run: $($loopStart.Substring(0, 7))..$($finalHead.Substring(0, 7))"
$openQuestions = Get-OpenQuestions $handoffPath
if ($openQuestions.Count -gt 0) {
    Write-Summary 'Open questions for Alex in the handoff:'
    foreach ($q in $openQuestions) { Write-Summary "- $q" }
}
if ($newQuestions.Count -gt 0) {
    Write-Summary 'For Alex lines added during this run (including ones the reviewer may have resolved since):'
    foreach ($q in $newQuestions) { Write-Summary "- $q" }
}
Write-Summary "Run folder: $runDir"
exit $exitCode
