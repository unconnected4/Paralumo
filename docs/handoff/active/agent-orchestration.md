---
task: agent-orchestration
title: Automate the implement/review loop between agents
status: in-progress
branch: main
started: 2026-09-29T14:39:07Z
last_updated: 2026-10-26T17:25:00Z
last_commit: db40d12
agents: [ClaudeC-Fable]
related_decisions: [docs/orchestration.md, AGENTS.md, docs/handoff/TEMPLATE.md]
---

# Automate the implement/review loop between agents

## Goal and acceptance
A script that runs agents on one task without Alex triggering each one: implementers in a fixed order, cycling until none has anything left to do, then a reviewer that closes the task, records findings for another cycle, or escalates to Alex. Design: `docs/orchestration.md`. Done when `scripts/orchestrate/orchestrate.ps1 -Task <slug>` runs at least one full implementation pass and one review of a real task end to end, stops on the documented conditions, and its logs land in `AgentsOutput/orchestrator/`.

## Current state
The driver, role table and prompts implement the pipeline Alex specified on 2026-10-26 (see Decisions). Verified in a scratch clone: dry runs for the first implementer and for the reviewer render the right prompt and command (model flag included); `-StartWith` rejects unknown roles; a role that exits without committing and a role that exits non-zero stop the loop with the reason; the final report lists the handoff's open `For Alex:` questions.

Not yet verified: every path that needs a committing agent (the nothing-to-do streak, the per-cycle cap, implementer `blocked` → review, reviewer `done` / `in-progress` / `blocked`, stale `last_commit` warning, archived-handoff detection), and a real round with any CLI. Only Claude Code is installed; the Gemini and Codex CLIs are not, so the `gemini` and `gpt` rows are configured from documentation and unverified (model ids, flags).

## Done
- Design agreed with Alex and recorded in `docs/orchestration.md`, 25d1652.
- Driver, role table and prompts for the first implement ↔ review design, c0d65fe, 02f0e94 (committed by Alex; the auto-mode classifier refused to commit files that configure permission-bypassing agents).
- Per-role `model` field filled into a `{model}` placeholder in `args`, 02f0e94.
- Pipeline rewritten to ordered implementers + reviewer with the nothing-to-do streak, per-cycle cap, implementer `blocked` hand-over, reviewer question resolution, `ready-for-review` status (also added to `docs/handoff/TEMPLATE.md`), and a final report of open questions. Design doc rewritten accordingly.

## Tried and rejected
- Agent triggers the next agent itself (Alex's first framing): rejected in favour of an external driver loop, because a crashed or forgetful agent silently breaks the chain and a hook firing inside a live session is fragile. Alex agreed.
- Agent chooses its successor: rejected; the agent reports `status`, the driver's pipeline routes.
- Multi-line prompt on the command line: rejected because PowerShell 5.1 quoting of native-command arguments with newlines and quotes is unreliable; the driver writes the rendered prompt to a file and passes a one-line pointer instead.
- `Join-Path $PSScriptRoot` as a parameter default: fails because `$PSScriptRoot` is not set while defaults are evaluated under `-File`. Resolved from `$MyInvocation.MyCommand.Path` in the body.
- Reading `$proc.ExitCode` after `Start-Process -PassThru` + `WaitForExit`: empty in PowerShell 5.1 unless `$proc.Handle` is read first.
- Detecting "nothing to do" only from code changes: rejected as the sole signal because a legitimate implementer round can change only docs; an explicit `ready-for-review` status is the primary signal, and "in-progress without code change" is a fallback that also counts as nothing to do.
- Stopping the loop on any new `For Alex:` line: rejected; the reviewer tries to resolve questions first (by Alex, 2026-10-26: Fable "tries to resolve them and forwards to Alex if it can't"), and only `blocked` stops the loop.

## Decisions made during this task
- Headless runs with auto-approved permissions inside the repo (by Alex, 2026-09-29): unattended agents cannot answer prompts.
- Pipeline (by Alex, 2026-10-26): implementation runs Opus, Gemini 3.7, GPT-6.1 Sol in that order, repeating "until none of the agents has anything to do for the current task"; then Fable reviews; comments start another implementation cycle; questions to Alex are first attempted by Fable and forwarded only if it can't resolve them. Replaces the 2026-09-29 fixed implement ↔ review pair.
- Per-role `model` in `roles.json` (asked by Alex, 2026-09-29), filled into a `{model}` placeholder in `args` because each CLI spells the flag differently.
- Driver in PowerShell 5.1 with a JSON role table: matches the host shell and needs no extra runtime. Run with `-ExecutionPolicy Bypass`, which is per process, rather than changing the machine's policy.
- Driver tests run in a scratch clone with stand-in role commands, never against the real history.
- Reviewer answers to questions are appended as `→ <answer> (resolved by <agent>, <date>: <evidence>)` and never tagged `by Alex`, so `git grep "by Alex"` keeps listing only Alex's own answers.

## Open questions
- For Alex: agent names for the headless roles are provisional, following the visible convention `<Tool><C for CLI>-<Model>`: `ClaudeC-Opus5.5`, `GeminiC-3.7`, `CodexC-GPT6.1Sol`, `ClaudeC-Fable`. `ClaudeC-Opus5.5` was already used by an earlier interactive session. Confirm or rename them in `roles.json` before the first real run, since they end up in commit subjects. Also: should they be added to the known-agents list in `AGENTS.md` now that the automation exists?
- For Alex: model ids and flags for the `gemini` and `gpt` rows (`gemini-3.7-pro` with `-m` and `--yolo`; `gpt-6.1-sol` with `codex exec --model ... --dangerously-bypass-approvals-and-sandbox`) are unverified guesses because neither CLI is installed. Check them against the installed CLIs. Until then, run with a config that lists only `opus` as implementer.
- For Alex: the auto-mode classifier blocks this session from creating a stand-in agent script that edits the handoff and commits (it looks like an unattended agent), so the routing paths listed under Current state are untested. Either run them yourself with a small committing stand-in wired into a test copy of `roles.json` under `-Config`, or accept the first real run as the test.

## Next step
Run the first real loop with the installed CLI only: a copy of `roles.json` with `"implementers": ["opus"]`, on `step1-backend`, with `-MaxRounds 3` so you get one implementer round, the streak completing on the second, and one review. Read `AgentsOutput/orchestrator/<run>/summary.md` and the agents' commits, then adjust prompts or checks based on what the real agents got wrong. When the Gemini and Codex CLIs are installed, verify their rows with `-DryRun -StartWith gemini` / `-StartWith gpt` and a one-round run each.

## Session log
- 2026-09-29T14:39:07Z ClaudeC-Fable: Agreed the design with Alex, wrote `docs/orchestration.md`, blocked on the permission classifier before any script was created.
- 2026-09-29T14:55:00Z ClaudeC-Fable: Alex asked to create the script in the project. Built driver, role table and prompts; tested dry run and two stop paths in a scratch clone; classifier blocked a committing stand-in agent for the remaining paths. Added the per-role `model` field. Alex committed by hand (c0d65fe, 02f0e94).
- 2026-10-26T17:25:00Z ClaudeC-Fable: Rewrote the pipeline to Alex's new spec (ordered implementers Opus → Gemini 3.7 → GPT-6.1 Sol cycling until nothing left, then Fable review with question resolution). Added `ready-for-review`, per-cycle cap, open-questions report. Tested dry runs and stop paths in the scratch clone.

## Outcome
<!-- Filled in only when archiving. -->
