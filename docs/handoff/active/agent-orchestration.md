---
task: agent-orchestration
title: Automate the implement/review loop between agents
status: in-progress
branch: main
started: 2026-09-29T14:39:07Z
last_updated: 2026-11-06T12:20:00Z
last_commit: 9a6b99e
agents: [ClaudeC-Fable, AgyC-Gemini4Argon, CodexC-GPT5.6Terra]
related_decisions: [docs/orchestration.md, AGENTS.md, docs/handoff/TEMPLATE.md]
---

# Automate the implement/review loop between agents

## Goal and acceptance
A script that runs agents on one task without Alex triggering each one: implementers in a fixed order, cycling until none has anything left to do, then a reviewer that closes the task, records findings for another cycle, or escalates to Alex. Design: `docs/orchestration.md`. Done when `scripts/orchestrate/orchestrate.ps1 -Task <slug>` runs at least one full implementation pass and one review of a real task end to end, stops on the documented conditions, and its logs land in `AgentsOutput/orchestrator/`.

## Current state
The driver, role table and prompts implement the pipeline Alex specified on 2026-10-26 (see Decisions). Verified in a scratch clone: dry runs for the first implementer and for the reviewer render the right prompt and command (model flag included); `-StartWith` rejects unknown roles; a role that exits without committing and a role that exits non-zero stop the loop with the reason; the final report lists the handoff's open `For Alex:` questions. Codex CLI 0.164.0 accepts the configured `exec` flags, and a minimal read-only invocation verified `gpt-5.6-terra` against the signed-in account. The orchestrator itself has not yet completed a Codex round. The updated Codex dry run rendered the expected command and completed successfully after fixing a strict-mode scalar `.Count` bug in final reporting when exactly one open Alex question exists.

The `gemini` role has been switched to the **Antigravity CLI (`agy`)** (`1.2.11`) after `@google/gemini-cli` rejected Google sign-in for individuals (migrated to Antigravity):
- `roles.json` `gemini` row now runs `agy` (`C:\Users\inbox\AppData\Local\agy\bin\agy.exe`) with model `gemini-4-argon-high` (agent `AgyC-Gemini4Argon`) and args `["-p", "{prompt}", "--model", "{model}", "--dangerously-skip-permissions", "--output-format", "text"]`. Verified headless invocation via `Start-Process` returns exit code `0`.
- `orchestrate.ps1` uses `Get-Command -CommandType Application` (so `.ps1` shims are skipped) and `taskkill /PID $proc.Id /T /F` on timeout.

**First complete real run, 2026-11-06** (`AgentsOutput/orchestrator/20261106T104319Z-step1-backend`, opus-only test config, 15 minutes, 2 rounds). Before it, Alex had Opus adopt and commit the interrupted backend work (27cb309). Then: round 1, Opus found nothing left, re-ran the checks and set `ready-for-review` (5864b14); the driver counted a streak of 1 of 1 and started the review. Round 2, Fable re-ran all checks, booted the app, committed small fixes, resolved one `For Alex:` question from `docs/plan.md`, moved the other to `web/intent.md`, archived the handoff as done and opened `e2e-smoke` for the follow-up (b74cc11, 9a6b99e). The driver detected the archived handoff and stopped with exit 0. Verified by this run: `ready-for-review` → streak → review, reviewer `done`, archived-handoff detection, the final report of `For Alex:` lines.

Found in that run and fixed: non-ASCII text was garbled. The summary showed `Γ£ô` for `✓` (git output decoded with the console's OEM code page), and the rendered review prompt showed `â†’` for `→` (templates read as ANSI). `Invoke-Git` now switches the console output encoding to UTF-8 for the call and restores it; every `Get-Content` of repo files and logs passes `-Encoding UTF8`.

Not yet verified: the streak with three implementers, the per-cycle cap, implementer `blocked` → review, reviewer `in-progress` and `blocked`, the stale `last_commit` warning, and `agy` / `codex` as committing implementers on a real task (Codex did one handoff-only round on 2026-10-28).

## Done
- Design agreed with Alex and recorded in `docs/orchestration.md`, 25d1652.
- Driver, role table and prompts for the first implement ↔ review design, c0d65fe, 02f0e94 (committed by Alex; the auto-mode classifier refused to commit files that configure permission-bypassing agents).
- Per-role `model` field filled into a `{model}` placeholder in `args`, 02f0e94.
- Pipeline rewritten to ordered implementers + reviewer with the nothing-to-do streak, per-cycle cap, implementer `blocked` hand-over, reviewer question resolution, `ready-for-review` status (also added to `docs/handoff/TEMPLATE.md`), and a final report of open questions. Design doc rewritten accordingly, dd81c4a.
- Switched the `gemini` role to Antigravity CLI (`agy`, model `gemini-4-argon-high`, agent `AgyC-Gemini4Argon`) and hardened `orchestrate.ps1` (`-CommandType Application`, `taskkill /T /F`).

- Agent names aligned across the project (by Alex, 2026-10-28): the four headless names from `roles.json` plus the interactive ones from history are listed with their roles in `AGENTS.md`, the "incomplete on purpose" note is gone, and `docs/orchestration.md` names the agent per role.
- Codex implementer model and name updated (by Alex, 2026-10-28): `gpt-5.6-terra` is accepted by the signed-in Codex CLI, so the role and agent are now `CodexC-GPT5.6Terra`.
- Fixed final report handling for exactly one open Alex question by forcing `Get-OpenQuestions` output to an array.

- Recovery from an interrupted round: a dirty tree at startup now names the unfinished run and round, and `-AdoptUncommitted` starts anyway and tells the first round to inspect, finish, verify and commit the leftovers. Documented in `docs/orchestration.md` ("Recovering from an interrupted round"). Tested in a scratch clone with a dirty tree and a fake unfinished run folder: the error message, the adopt path and the rendered `{notes}`.

## Tried and rejected
- Agent triggers the next agent itself (Alex's first framing): rejected in favour of an external driver loop, because a crashed or forgetful agent silently breaks the chain and a hook firing inside a live session is fragile. Alex agreed.
- Agent chooses its successor: rejected; the agent reports `status`, the driver's pipeline routes.
- Multi-line prompt on the command line: rejected because PowerShell 5.1 quoting of native-command arguments with newlines and quotes is unreliable; the driver writes the rendered prompt to a file and passes a one-line pointer instead.
- `Join-Path $PSScriptRoot` as a parameter default: fails because `$PSScriptRoot` is not set while defaults are evaluated under `-File`. Resolved from `$MyInvocation.MyCommand.Path` in the body.
- Reading `$proc.ExitCode` after `Start-Process -PassThru` + `WaitForExit`: empty in PowerShell 5.1 unless `$proc.Handle` is read first.
- Plain `(Get-Command $roleCfg.command).Source` with `Start-Process`: fails for npm-installed CLIs because PowerShell returns `.ps1` (`ExternalScript`) before `.cmd`/`.exe` (`Application`), and `Start-Process -NoNewWindow` throws `%1 is not a valid Win32 application` on `.ps1` files. Fixed with `-CommandType Application`.
- `@google/gemini-cli` (`gemini`) with Google OAuth: rejected; Google retired individual Code Assist sign-in on `gemini-cli` in favour of Antigravity (`agy`). Switched `roles.json` to `agy` (by Alex, 2026-10-28).
- Detecting "nothing to do" only from code changes: rejected as the sole signal because a legitimate implementer round can change only docs; an explicit `ready-for-review` status is the primary signal, and "in-progress without code change" is a fallback that also counts as nothing to do.
- Stopping the loop on any new `For Alex:` line: rejected; the reviewer tries to resolve questions first (by Alex, 2026-10-26: Fable "tries to resolve them and forwards to Alex if it can't"), and only `blocked` stops the loop.

## Decisions made during this task
- Headless runs with auto-approved permissions inside the repo (by Alex, 2026-09-29): unattended agents cannot answer prompts.
- Pipeline (by Alex, 2026-10-26): implementation runs Opus, Gemini, GPT-6.1 Sol in that order, repeating "until none of the agents has anything to do for the current task"; then Fable reviews; comments start another implementation cycle; questions to Alex are first attempted by Fable and forwarded only if it can't resolve them. Replaces the 2026-09-29 fixed implement ↔ review pair.
- Codex model (by Alex, 2026-10-28): use GPT-5.6 Terra for the Codex implementer after GPT-6.1 Sol was rejected by the signed-in Codex account. A read-only `codex exec` invocation accepted `gpt-5.6-terra`.
- Gemini role uses Antigravity CLI (`agy`) (by Alex, 2026-10-28): command `agy`, model `gemini-4-argon-high`, agent `AgyC-Gemini4Argon`, args `-p {prompt} --model {model} --dangerously-skip-permissions --output-format text`.
- Per-role `model` in `roles.json` (asked by Alex, 2026-09-29), filled into a `{model}` placeholder in `args` because each CLI spells the flag differently.
- Driver in PowerShell 5.1 with a JSON role table: matches the host shell and needs no extra runtime. Run with `-ExecutionPolicy Bypass`, which is per process, rather than changing the machine's policy.
- Driver tests run in a scratch clone with stand-in role commands, never against the real history.
- Reviewer answers to questions are appended as `→ <answer> (resolved by <agent>, <date>: <evidence>)` and never tagged `by Alex`, so `git grep "by Alex"` keeps listing only Alex's own answers.

- Agent names identify tool + model, not a session, and may be reused by any session with that tool and model (by Alex, 2026-10-28: "it is ok to reuse names"). Convention `<Tool><C|D>-<Model>`; the list of record is in `AGENTS.md`.

## Open questions
- ~~For Alex: please run `claude auth login` in this host profile. `claude auth status` still reports `loggedIn: false`; this blocks every full live loop and the remaining end-to-end routing verification.~~ → No longer open: both Claude roles ran headless to completion on 2026-11-06 (run `20261106T104319Z-step1-backend`), so the CLI is signed in (closed by ClaudeC-Fable, 2026-11-06).
- ~~For Alex: agent names for the headless roles are provisional (`ClaudeC-Opus5.5`, `AgyC-Gemini4Argon`, `CodexC-GPT6.1Sol`, `ClaudeC-Fable`); `ClaudeC-Opus5.5` was already used by an earlier interactive session. Confirm or rename, and add to `AGENTS.md`?~~ → Names confirmed as they stand in `roles.json`; "align names and roles within the project. it is ok to reuse names" (by Alex, 2026-10-28). A name identifies tool + model, so interactive and headless sessions share it. All known names and their roles are now listed in `AGENTS.md`.
- ~~For Alex: model id and flags for the `gpt` row (`gpt-6.1-sol` with `codex exec --model ... --dangerously-bypass-approvals-and-sandbox`) are still unverified against `codex.exe`. Check them before running the `gpt` role.~~ → Codex CLI 0.164.0 accepted these flags; `gpt-6.1-sol` was rejected for the signed-in ChatGPT account. A read-only call using `gpt-5.6-terra` succeeded (resolved by CodexC-GPT5.6Terra, 2026-10-28).
- ~~For Alex: the routing paths that need a committing agent are untested, because the interactive Claude Code session (auto mode) is not allowed to create a stand-in script that commits on its own. Test them with such a script yourself, or accept the first real run as the test?~~ → First real run accepted as the test (by Alex, 2026-10-28). Alex ran the loop on this task with `-StartWith gpt`: the Codex round committed a handoff-only update and the driver correctly counted it as "nothing to do"; the following Opus round failed at startup because the Claude CLI's OAuth session had expired (`claude auth status` → `loggedIn: false`). Not a driver bug: sign in again with `claude auth login` and rerun.

## Next step
1. Run the full pipeline (all three implementers, default `roles.json`) on the next real task, `e2e-smoke`: `powershell -ExecutionPolicy Bypass -File scripts/orchestrate/orchestrate.ps1 -Task e2e-smoke`. This is the first run that exercises `agy` and `codex` as committing implementers, the three-implementer streak, and (if Fable has findings) a second cycle.
2. Read that run's `summary.md` and commits, fix what the real agents got wrong, then close this task: the acceptance criterion (one implementation pass and one review of a real task, end to end) was met on 2026-11-06 with one implementer, so only step 1 stands between this handoff and the archive.
3. Still unverified by any run: three-implementer streak, per-cycle cap, implementer `blocked` → review, reviewer `in-progress` (findings → new cycle) and reviewer `blocked`, stale `last_commit` warning.

## Session log
- 2026-09-29T14:39:07Z ClaudeC-Fable: Agreed the design with Alex, wrote `docs/orchestration.md`, blocked on the permission classifier before any script was created.
- 2026-09-29T14:55:00Z ClaudeC-Fable: Alex asked to create the script in the project. Built driver, role table and prompts; tested dry run and two stop paths in a scratch clone; classifier blocked a committing stand-in agent for the remaining paths. Added the per-role `model` field. Alex committed by hand (c0d65fe, 02f0e94).
- 2026-10-26T17:25:00Z ClaudeC-Fable: Rewrote the pipeline to Alex's new spec (ordered implementers Opus → Gemini 3.7 → GPT-6.1 Sol cycling until nothing left, then Fable review with question resolution). Added `ready-for-review`, per-cycle cap, open-questions report. Tested dry runs and stop paths in the scratch clone.
- 2026-10-28T12:38:00Z Antigravity: Switched the `gemini` role to Antigravity CLI (`agy` with `gemini-4-argon-high`), verified headless execution, and hardened `orchestrate.ps1` (`-CommandType Application`, `taskkill /T /F`).

- 2026-10-28T13:30:00Z ClaudeC-Fable: Alex confirmed the agent names and allowed reuse. Listed all known agents with roles in `AGENTS.md`, aligned `docs/orchestration.md`, closed the names question.
- 2026-10-28T13:25:11Z CodexC-GPT5.6Terra: Verified `gpt-5.6-terra` with a minimal read-only Codex CLI call, updated the `gpt` role and agent name across config and docs. The orchestrator dry run requires a clean tree, so it remains for after this commit.
- 2026-10-28T13:26:45Z CodexC-GPT5.6Terra: Committed model/name updates as `7169140`; dry run rendered the correct Codex command and revealed the single-question `.Count` error. Forced the question list to an array; rerun the dry run after committing this fix.
- 2026-10-28T13:27:45Z CodexC-GPT5.6Terra: Dry run passed on commit `77feb81`; it printed the configured Terra command and listed the one remaining open question without errors.
- 2026-10-28T13:29:34Z CodexC-GPT5.6Terra: As the live-loop implementer, parsed `orchestrate.ps1` successfully and re-ran a clean-tree dry run from `c7a6bec`; it rendered the configured Opus command and final report correctly. No scoped code change remains before the reviewer assesses the live round; a committing agent/review routing path is still required for end-to-end acceptance.

- 2026-10-28T17:37:18Z ClaudeC-Fable: Diagnosed Alex's first live run: Codex round routed correctly, Opus round failed because the Claude CLI was signed out. Driver now prints the log tail on a failed round and checks all pipeline CLIs before round 1. Closed the testing question.
- 2026-10-28T17:39:53Z CodexC-GPT5.6Terra: Verified the PowerShell parser and a clean-tree `-StartWith gpt` dry run at `62ec3d4`; its expected Terra command rendered successfully. `claude auth status` still reports `loggedIn: false`, so recorded the authentication requirement as an open Alex question and set the task blocked.

- 2026-11-06T10:13:03Z ClaudeC-Fable: Alex's rerun on `step1-backend` was refused for a dirty tree: the 2026-10-28 17:42 Opus round had been interrupted with its work uncommitted. Added the unfinished-run hint and `-AdoptUncommitted`, documented the recovery, set status back to in-progress (auth blocker is gone).
- 2026-11-06T12:20:00Z ClaudeC-Fable: Checked the state after Alex's runs: Opus adopted and committed the backend (27cb309), then the first complete orchestrated run closed `step1-backend` in two rounds. Recorded what it verified, fixed the UTF-8 decoding of git output and templates that it exposed, closed the sign-in question, pointed Next step at a full-pipeline run on `e2e-smoke`.

## Outcome
<!-- Filled in only when archiving. -->
