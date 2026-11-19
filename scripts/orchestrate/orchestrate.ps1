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

.PARAMETER AdoptUncommitted
Start even though the working tree has uncommitted changes, and tell the first round to
inspect, finish, verify and commit them. For recovering from a round that was interrupted
or killed on timeout. Without it a dirty tree stops the driver before round 1.

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
    [switch]$AdoptUncommitted,
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
    # git writes UTF-8; PowerShell 5.1 decodes native output with the console's OEM code page
    # unless told otherwise, which turns non-ASCII text from handoffs into mojibake in the summary.
    $prevEncoding = [Console]::OutputEncoding
    try {
        [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
        $out = & git @GitArgs
        if ($LASTEXITCODE -ne 0) { throw "git $($GitArgs -join ' ') failed (exit $LASTEXITCODE)" }
        return $out
    } finally {
        [Console]::OutputEncoding = $prevEncoding
    }
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
    $lines = Get-Content -Encoding UTF8 -LiteralPath $Path
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
    return @(Get-Content -Encoding UTF8 -LiteralPath $Path | ForEach-Object { $_.Trim() } | Where-Object {
        $_ -match '^-\s*For Alex:' -and $_ -notmatch '\(resolved by ' -and $_ -notmatch '\(by Alex'
    } | ForEach-Object { $_ -replace '^-\s*', '' })
}

function Write-Summary {
    param([string]$Text)
    Write-Host $Text
    Add-Content -LiteralPath $script:summaryPath -Value $Text -Encoding UTF8
}

# ---- database owned by the driver (roles.json "database"; docs/orchestration.md, "The database")

function Invoke-PgTool {
    # Runs a Postgres client tool quietly and returns its exit code. Native stderr would become
    # an ErrorRecord under $ErrorActionPreference = 'Stop', so relax it for this call only.
    param([string]$Exe, [string[]]$ToolArgs)
    $prev = $ErrorActionPreference
    try {
        $ErrorActionPreference = 'Continue'
        & $Exe @ToolArgs *> $null
        return $LASTEXITCODE
    } finally {
        $ErrorActionPreference = $prev
    }
}

function Test-DatabaseReady {
    return ((Invoke-PgTool $script:db.isReady @('-h', $script:db.host, '-p', $script:db.port, '-t', '5')) -eq 0)
}

function Test-DatabaseRunning {
    # pg_ctl status: 0 = a postmaster is running on this data directory, 3 = none.
    return ((Invoke-PgTool $script:db.pgCtl @('-D', $script:db.dataDir, 'status')) -eq 0)
}

function Stop-Database {
    $code = Invoke-PgTool $script:db.pgCtl @('-D', $script:db.dataDir, '-m', 'fast', '-w', '-t', '60', 'stop')
    if ($code -ne 0 -and (Test-DatabaseRunning)) { throw "Could not stop Postgres (pg_ctl exit $code); see $($script:db.log)" }
}

function Start-Database {
    # Detached on purpose. A server started from an agent's shell shares that session's console
    # and dies when the session ends (new backends fail with 0xC0000142), and it holds the agent's
    # output handles, which can keep the CLI from exiting. Start-Process without -NoNewWindow or
    # redirection uses ShellExecute: pg_ctl and the postmaster get a hidden console of their own
    # and inherit no handles from the driver or any agent.
    $argString = ConvertTo-ArgumentString @('-D', $script:db.dataDir, '-l', $script:db.log, '-w', '-t', '120', 'start')
    $p = Start-Process -FilePath $script:db.pgCtl -ArgumentList $argString -WindowStyle Hidden -PassThru
    $null = $p.Handle   # PowerShell 5.1: ExitCode stays empty unless the handle is read before the wait
    # WaitForExit on the object, not Start-Process -Wait: in 5.1 -Wait also waits for the
    # postmaster, which never exits.
    if (-not $p.WaitForExit(150000)) {
        try { $p.Kill() } catch { }
        throw "pg_ctl start did not return within 150 s; see $($script:db.log)"
    }
    if ($p.ExitCode -ne 0 -or -not (Test-DatabaseReady)) {
        throw "Postgres did not start (pg_ctl exit $($p.ExitCode)); see $($script:db.log)"
    }
}

function Assert-DatabaseUp {
    # Before every round: the agent must find the database up. Restart it if it died.
    param([string]$When)
    if (Test-DatabaseReady) { return }
    Write-Summary "Warning: Postgres was not accepting connections $When; restarting it. Log: $($script:db.log)"
    if (Test-DatabaseRunning) { Stop-Database }
    Start-Database
}

# ---------------------------------------------------------------- setup

$repoRoot = (Invoke-Git @('rev-parse', '--show-toplevel')) | Select-Object -First 1
$repoRoot = (Resolve-Path -LiteralPath $repoRoot).Path
Set-Location -LiteralPath $repoRoot

if (-not (Test-Path -LiteralPath $Config)) { throw "Config not found: $Config" }
$cfg = Get-Content -Encoding UTF8 -LiteralPath $Config -Raw | ConvertFrom-Json

$handoffRel = "docs/handoff/active/$Task.md"
$handoffPath = Join-Path $repoRoot $handoffRel
if (-not (Test-Path -LiteralPath $handoffPath)) {
    throw "Handoff file not found: $handoffRel. Create it from docs/handoff/TEMPLATE.md with Goal and Next step filled in."
}

$dirty = Invoke-Git @('status', '--porcelain')
$adoptNote = $null
if ($dirty) {
    # A round that was interrupted (Ctrl+C, closed terminal, sleep) or killed on timeout leaves
    # its work uncommitted. Look for the evidence: the task's latest run folder without a Result.
    $hint = ''
    # Dry runs execute nothing, so they are skipped when looking for the last real run.
    $lastRun = Get-ChildItem -LiteralPath (Join-Path $repoRoot 'AgentsOutput/orchestrator') -Directory -Filter "*-$Task" -ErrorAction SilentlyContinue |
        Where-Object {
            $s = Join-Path $_.FullName 'summary.md'
            (Test-Path -LiteralPath $s) -and -not (Select-String -LiteralPath $s -Pattern '^Dry run:' -Quiet)
        } | Sort-Object Name | Select-Object -Last 1
    if ($lastRun) {
        $lastSummary = Join-Path $lastRun.FullName 'summary.md'
        if ((Test-Path -LiteralPath $lastSummary) -and -not (Select-String -LiteralPath $lastSummary -Pattern '^## Result' -Quiet)) {
            $lastRound = @(Select-String -LiteralPath $lastSummary -Pattern '^## Round ') | Select-Object -Last 1
            $where = if ($lastRound) { " during '$($lastRound.Line.Trim())'" } else { '' }
            $hint = "The previous run of this task ($($lastRun.Name)) never finished: the driver was interrupted$where. These changes are probably that agent's unfinished work."
        }
    }
    if (-not $AdoptUncommitted) {
        throw "Working tree is not clean:`n$($dirty -join "`n")`n$hint`nEither commit or stash the changes yourself (git stash -u), or rerun with -AdoptUncommitted to let the first round inspect, finish, verify and commit them."
    }
    $adoptNote = "the working tree contains uncommitted changes left behind by an earlier round that did not finish ($(@($dirty).Count) paths; run git status). $hint Do not discard them unseen: inspect them, decide whether they are sound, finish and verify the work with the project's checks, and commit everything per protocol. Describe in the handoff what you found and what you did with it; if you throw any of it away, record why."
}

$implementers = @($cfg.pipeline.implementers | ForEach-Object { [string]$_ })
$reviewer = [string]$cfg.pipeline.reviewer
if ($implementers.Count -eq 0) { throw "pipeline.implementers is empty in $Config" }
if (-not $reviewer) { throw "pipeline.reviewer is missing in $Config" }

$roleNames = @($cfg.roles.PSObject.Properties.Name)
foreach ($name in ($implementers + $reviewer)) {
    if ($roleNames -notcontains $name) { throw "Role '$name' is used in pipeline but not defined in roles. Roles: $($roleNames -join ', ')" }
    # Fail before the first round rather than in the round that needs a missing CLI.
    $cmdName = [string]$cfg.roles.$name.command
    if (-not (Get-Command $cmdName -CommandType Application -ErrorAction SilentlyContinue)) {
        throw "Role '$name' needs the command '$cmdName', which is not on PATH. Install it or remove the role from the pipeline."
    }
}

if ($MaxRounds -le 0) { $MaxRounds = [int]$cfg.limits.maxRounds }
$maxImplRounds = [int]$cfg.limits.maxImplementationRoundsPerCycle
$timeoutMs = [int]$cfg.limits.roundTimeoutMinutes * 60 * 1000

# Optional "env" object in the config: variables set for this process, so every agent inherits them.
$envLabels = @()
if (($cfg.PSObject.Properties.Name -contains 'env') -and $cfg.env) {
    foreach ($p in $cfg.env.PSObject.Properties) {
        [Environment]::SetEnvironmentVariable($p.Name, [string]$p.Value, 'Process')
        $envLabels += "$($p.Name)=$($p.Value)"
    }
}

# Optional "database" object: the Postgres the driver runs for the whole run (agents never start it).
$script:db = $null
$databaseNote = 'the driver does not manage a database in this run (no "database" in the config).'
if (($cfg.PSObject.Properties.Name -contains 'database') -and $cfg.database) {
    # Paths are relative to the repo root; Combine also accepts absolute ones (a test clone uses those).
    $binDir = [System.IO.Path]::Combine($repoRoot, [string]$cfg.database.bin)
    $script:db = @{
        pgCtl   = Join-Path $binDir 'pg_ctl.exe'
        isReady = Join-Path $binDir 'pg_isready.exe'
        dataDir = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($repoRoot, [string]$cfg.database.dataDir))
        log     = [System.IO.Path]::GetFullPath([System.IO.Path]::Combine($repoRoot, [string]$cfg.database.log))
        host    = [string]$cfg.database.host
        port    = [string]$cfg.database.port
    }
    foreach ($path in @($script:db.pgCtl, $script:db.isReady, $script:db.dataDir)) {
        if (-not (Test-Path -LiteralPath $path)) { throw "database: $path not found (check database.bin and database.dataDir in $Config)" }
    }
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $script:db.log) | Out-Null
    $databaseNote = "the driver runs PostgreSQL for this whole run at $($script:db.host):$($script:db.port) (data directory ``$([string]$cfg.database.dataDir)``, log ``$([string]$cfg.database.log)``), and it is up when your round starts. Use it as it is. Never start, stop, restart or re-initialise it, and never run a second instance, not even on a copy of the data directory: a server started from your session dies when your session ends, and it can keep your CLI from exiting. If ``pg_isready -h $($script:db.host) -p $($script:db.port)`` fails, don't work around it: record it in the handoff as an environment problem, say which checks you could not run, and finish the round."
}

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

$commonTemplate = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $scriptDir 'prompts/_common.md') -Raw
$implementerLabels = ($implementers | ForEach-Object { "$_ ($($cfg.roles.$_.agent))" }) -join ', '
$loopStart = (Invoke-Git @('rev-parse', 'HEAD')) | Select-Object -First 1

Write-Summary "# Orchestrator run $runStamp"
Write-Summary "Task: $Task | start: $loopStart | config: $Config | max rounds: $MaxRounds"
Write-Summary "Implementers: $implementerLabels | reviewer: $reviewer ($($cfg.roles.$reviewer.agent))"
if ($envLabels.Count -gt 0) { Write-Summary "Environment for agents: $($envLabels -join '; ')" }
if ($script:db -and -not $DryRun) {
    # A server that is already running was started by someone else (an agent, a terminal) and
    # shares that process's console, so it can die mid-run. Restart it under the driver.
    if (Test-DatabaseRunning) {
        Write-Summary "Database: restarting the running Postgres so it runs detached from any session ($($script:db.dataDir))."
        Stop-Database
    }
    Start-Database
    Write-Summary "Database: Postgres up at $($script:db.host):$($script:db.port), started by the driver. Log: $($script:db.log)"
}
Write-Summary ''

# ---------------------------------------------------------------- loop

$round = 0
$cycle = 1                 # review cycle: implementation phase + the review that ends it
$nothingStreak = 0         # consecutive implementers that reported nothing left to do
$implRoundsThisCycle = 0
$notes = 'none'
if ($adoptNote) {
    $notes = $adoptNote
    Write-Summary "Adopting uncommitted changes ($(@($dirty).Count) paths): round 1 is told to inspect, finish and commit them."
    Write-Summary ''
}
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
    $roleTemplate = Get-Content -Encoding UTF8 -LiteralPath (Join-Path $repoRoot $roleCfg.prompt) -Raw
    $prompt = $roleTemplate.Replace('{common}', $commonTemplate.TrimEnd())
    $prompt = $prompt.Replace('{agent}', $agent).Replace('{task}', $Task).Replace('{handoff}', $handoffRel)
    $prompt = $prompt.Replace('{round}', [string]$round).Replace('{cycle}', [string]$cycle)
    $prompt = $prompt.Replace('{implementers}', $implementerLabels).Replace('{notes}', $notes)
    $prompt = $prompt.Replace('{database}', $databaseNote)
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

    if ($script:db) {
        try { Assert-DatabaseUp "before round $round" }
        catch { $stopReason = "round $round ($role) not started: $($_.Exception.Message)"; break }
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
    # Evidence only: the next round restarts it anyway. A database that went down during a round
    # means that round's checks may have run without it; the reviewer should know.
    if ($script:db -and -not (Test-DatabaseReady)) {
        Write-Summary "Warning: Postgres was down at the end of round $round; that round's backend checks may not have run against it. Log: $($script:db.log)"
    }

    # ---- protocol checks, same for every role
    if ($proc.ExitCode -ne 0) {
        # Show the tail of both logs so the cause (e.g. an expired CLI login) is visible without opening files.
        $tail = @(Get-Content -Encoding UTF8 -LiteralPath $stdoutPath, $stderrPath -ErrorAction SilentlyContinue |
            Where-Object { $_.Trim() } | Select-Object -Last 5)
        $tailText = if ($tail.Count -gt 0) { $tail -join "`n  " } else { '(no output)' }
        $stopReason = "round $round ($role) exited with code $($proc.ExitCode). Last output:`n  $tailText`nFull logs: $stdoutPath, $stderrPath"
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
$openQuestions = @(Get-OpenQuestions $handoffPath)
if ($openQuestions.Count -gt 0) {
    Write-Summary 'Open questions for Alex in the handoff:'
    foreach ($q in $openQuestions) { Write-Summary "- $q" }
}
if ($newQuestions.Count -gt 0) {
    Write-Summary 'For Alex lines added during this run (including ones the reviewer may have resolved since):'
    foreach ($q in $newQuestions) { Write-Summary "- $q" }
}
if ($script:db -and -not $DryRun) {
    # Left running on purpose: it is detached, so it survives this terminal, and Alex's own
    # bootRun and checks use the same database.
    $dbState = if (Test-DatabaseReady) { 'still running (left up for local use)' } else { 'not running' }
    Write-Summary "Database: Postgres $dbState. Stop it with: `"$($script:db.pgCtl)`" -D `"$($script:db.dataDir)`" stop"
}
Write-Summary "Run folder: $runDir"
exit $exitCode
