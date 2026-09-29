<#
.SYNOPSIS
Runs coding agents in a fixed implement/review loop for one task.

.DESCRIPTION
Design and rules: docs/orchestration.md. Each round starts one role from roles.json,
waits for it to exit, checks that it committed and updated the handoff file, reads the
handoff status and routes to the next role. Stops on: done/abandoned/blocked status,
uncommitted changes, no new commit, handoff not updated, too many rounds without a code
change, the round limit, a timeout, or a non-zero exit code.

.PARAMETER Task
Handoff slug: docs/handoff/active/<Task>.md must exist.

.PARAMETER Config
Path to the role table. Default: roles.json next to this script.

.PARAMETER StartRole
Role to run first. Default: pipeline.start from the config.

.PARAMETER MaxRounds
Overrides limits.maxRounds from the config.

.PARAMETER DryRun
Render the first prompt and print the command without running anything.

.EXAMPLE
powershell -File scripts/orchestrate/orchestrate.ps1 -Task step1-backend
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)][string]$Task,
    [string]$Config = '',
    [string]$StartRole = '',
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

if ($MaxRounds -le 0) { $MaxRounds = [int]$cfg.limits.maxRounds }
$maxNoCode = [int]$cfg.limits.maxRoundsWithoutCodeChange
$timeoutMs = [int]$cfg.limits.roundTimeoutMinutes * 60 * 1000
if (-not $StartRole) { $StartRole = [string]$cfg.pipeline.start }

$roleNames = @($cfg.roles.PSObject.Properties.Name)
if ($roleNames -notcontains $StartRole) { throw "Unknown role '$StartRole'. Roles: $($roleNames -join ', ')" }

$runStamp = (Get-Date).ToUniversalTime().ToString('yyyyMMddTHHmmssZ')
$runDir = Join-Path $repoRoot "AgentsOutput/orchestrator/$runStamp-$Task"
New-Item -ItemType Directory -Force -Path $runDir | Out-Null
$script:summaryPath = Join-Path $runDir 'summary.md'

$commonTemplate = Get-Content -LiteralPath (Join-Path $scriptDir 'prompts/_common.md') -Raw
$loopStart = (Invoke-Git @('rev-parse', 'HEAD')) | Select-Object -First 1

Write-Summary "# Orchestrator run $runStamp"
Write-Summary "Task: $Task | start: $loopStart | config: $Config | max rounds: $MaxRounds"
Write-Summary ''

# ---------------------------------------------------------------- loop

$role = $StartRole
$round = 0
$roundsWithoutCode = 0
$notes = 'none'
$forAlex = New-Object System.Collections.Generic.List[string]
$stopReason = $null
$exitCode = 1

while ($round -lt $MaxRounds) {
    $round++
    $roleCfg = $cfg.roles.$role
    $agent = [string]$roleCfg.agent
    $roundTag = ('round-{0:d2}-{1}' -f $round, $role)
    $startHead = (Invoke-Git @('rev-parse', 'HEAD')) | Select-Object -First 1

    # Render the prompt to a file; the agent gets a one-line pointer to it.
    $roleTemplate = Get-Content -LiteralPath (Join-Path $repoRoot $roleCfg.prompt) -Raw
    $prompt = $roleTemplate.Replace('{common}', $commonTemplate.TrimEnd())
    $prompt = $prompt.Replace('{agent}', $agent).Replace('{task}', $Task).Replace('{handoff}', $handoffRel)
    $prompt = $prompt.Replace('{round}', [string]$round).Replace('{notes}', $notes)
    $promptPath = Join-Path $runDir "$roundTag.prompt.md"
    [System.IO.File]::WriteAllText($promptPath, $prompt, (New-Object System.Text.UTF8Encoding($false)))

    $pointer = "Read the file $promptPath and follow the instructions in it exactly."
    $argItems = @($roleCfg.args | ForEach-Object { ([string]$_).Replace('{prompt}', $pointer) })
    $argString = ConvertTo-ArgumentString $argItems
    $exe = (Get-Command $roleCfg.command -ErrorAction Stop).Source

    Write-Summary "## Round $round : $role ($agent)"
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
        try { $proc.Kill() } catch { }
        $stopReason = "round $round ($role) exceeded the timeout of $($cfg.limits.roundTimeoutMinutes) minutes and was killed"
        break
    }
    $elapsed = [int]((Get-Date) - $started).TotalMinutes
    Write-Summary "Exit code: $($proc.ExitCode) after $elapsed min. Logs: $stdoutPath"

    # ---- post-checks
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

    # New questions for Alex, collected for the final report.
    $diffLines = @(& git diff -U0 $startHead $endHead -- docs/handoff/)
    foreach ($line in $diffLines) {
        if ($line -match '^\+.*For Alex:') { $forAlex.Add(("round {0} ({1}): {2}" -f $round, $role, $line.Substring(1).Trim())) }
    }

    # Code change tracking: anything outside docs/handoff/ counts.
    $codeFiles = @($changed | Where-Object { $_ -notlike 'docs/handoff/*' })
    if ($codeFiles.Count -eq 0) { $roundsWithoutCode++ } else { $roundsWithoutCode = 0 }

    # Status: archived handoff means done.
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
    Write-Summary "Status: $status"

    switch ($status) {
        'done'      { $stopReason = "task done (status: done)"; $exitCode = 0 }
        'abandoned' { $stopReason = "task abandoned (status: abandoned)"; $exitCode = 0 }
        'blocked'   { $stopReason = "task blocked; Alex is needed (status: blocked)" }
        'in-progress' { }
        default     { $stopReason = "unrecognised status '$status' in $handoffRel" }
    }
    if ($stopReason) { break }

    if ($roundsWithoutCode -ge $maxNoCode) {
        $stopReason = "$roundsWithoutCode rounds in a row changed only docs/handoff/; the agents may be going in circles"
        break
    }

    $next = $cfg.pipeline.after.$role
    if (-not $next) { $stopReason = "no next role configured after '$role'"; break }
    $role = [string]$next
    Write-Summary ''
}

if (-not $stopReason) { $stopReason = "round limit of $MaxRounds reached" }

# ---------------------------------------------------------------- report

$finalHead = (Invoke-Git @('rev-parse', 'HEAD')) | Select-Object -First 1
Write-Summary ''
Write-Summary '## Result'
Write-Summary "Stopped after $round round(s): $stopReason"
Write-Summary "Commits this run: $($loopStart.Substring(0, 7))..$($finalHead.Substring(0, 7))"
if ($forAlex.Count -gt 0) {
    Write-Summary 'New questions for Alex:'
    foreach ($q in $forAlex) { Write-Summary "- $q" }
}
Write-Summary "Run folder: $runDir"
exit $exitCode
