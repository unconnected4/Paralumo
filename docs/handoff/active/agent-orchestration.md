---
task: agent-orchestration
title: Automate the implement/review loop between agents
status: in-progress
branch: main
started: 2026-09-29T14:39:07Z
last_updated: 2026-09-29T14:55:00Z
last_commit: 25d1652
agents: [ClaudeC-Fable]
related_decisions: [docs/orchestration.md, AGENTS.md]
---

# Automate the implement/review loop between agents

## Goal and acceptance
A script that runs agents in a fixed implement ↔ review loop for one task, so Alex no longer triggers each agent by hand. Design: `docs/orchestration.md`. Done when `scripts/orchestrate/orchestrate.ps1 -Task <slug>` runs at least one implement round and one review round of a real task end to end, stops on the documented conditions, and its logs land in `AgentsOutput/orchestrator/`.

## Current state
The driver, role table and prompt templates exist under `scripts/orchestrate/` and follow `docs/orchestration.md`. Verified in a scratch clone: dry run renders the prompt and prints the exact `claude.exe` command; a role that exits without committing stops the loop with "made no commit"; a role that exits non-zero stops with its exit code. Exit codes and stop reasons are correct.

Not yet verified: the paths that need a committing agent (`done`, `blocked`, stale `last_commit` warning, two rounds without code change, archived-handoff detection), and a real round with Claude Code. The Claude Code auto-mode classifier refused to let this session create a stand-in agent script that commits on its own, so those tests need Alex (see Open questions).

## Done
- Design agreed with Alex and recorded in `docs/orchestration.md`, 25d1652.
- `scripts/orchestrate/orchestrate.ps1`, `roles.json`, `prompts/_common.md`, `prompts/implement.md`, `prompts/review.md`; run command and a testing section added to the design doc; `AGENTS.md` repo map links to it.
- Per-role `model` field (asked by Alex), filled into a `{model}` placeholder in `args` so each CLI keeps its own flag; missing model with `{model}` in args is a startup error; the model is shown in each round's summary line. Verified by dry run in the scratch clone.

## Tried and rejected
- Agent triggers the next agent itself (Alex's first framing): rejected in favour of an external driver loop, because a crashed or forgetful agent silently breaks the chain and a hook firing inside a live session is fragile. Alex agreed.
- Agent chooses its successor: rejected for v1; the agent reports `status`, the driver's pipeline table routes.
- Multi-line prompt on the command line: rejected because PowerShell 5.1 quoting of native-command arguments with newlines and quotes is unreliable; the driver writes the rendered prompt to a file and passes a one-line pointer instead.
- `Join-Path $PSScriptRoot` as a parameter default: fails with an empty-string error, because `$PSScriptRoot` is not set while defaults are evaluated under `-File`. Resolved from `$MyInvocation.MyCommand.Path` in the body instead.
- Reading `$proc.ExitCode` after `Start-Process -PassThru` + `WaitForExit`: empty in PowerShell 5.1 unless `$proc.Handle` is read first. Both bugs were found by the scratch-clone tests.

## Decisions made during this task
- Fixed implement ↔ review pipeline for v1 (by Alex): enough for now.
- Headless runs with auto-approved permissions inside the repo (by Alex): unattended agents cannot answer prompts.
- Gemini CLI will be installed by Alex; until then all roles run Claude Code.
- Driver in PowerShell 5.1 with a JSON role table: matches the host shell and needs no extra runtime. Run with `-ExecutionPolicy Bypass`, which is per process, rather than changing the machine's policy.
- Driver tests run in a scratch clone with stand-in role commands, never against the real history.

## Open questions
- For Alex: the auto-mode classifier blocks this session from creating a stand-in agent script that edits the handoff and commits (it looks like an unattended agent). Either run the remaining stop-path tests yourself (a small script that sets `status` and commits, wired into a test copy of `roles.json` under `-Config`), or run a session with manual permission approval for it, or accept a real Claude round as the first end-to-end test.
- For Alex: agent names for the headless roles. A headless Claude launched by the driver is a different instance from the interactive `ClaudeC-Fable` session, and the Gemini CLI agent needs a name too. `roles.json` reuses `ClaudeC-Fable` for both roles until assigned, which makes `git log --oneline` less informative.
- Next agent: should a new `For Alex:` line stop the loop even when the agent keeps `status: in-progress`? Design says no (report at the end); revisit if agents keep working past questions that should have blocked them.

## Next step
Run the untested stop paths (see Current state) with a committing stand-in, then the first real round: `powershell -ExecutionPolicy Bypass -File scripts/orchestrate/orchestrate.ps1 -Task step1-backend -MaxRounds 2`. Read `AgentsOutput/orchestrator/<run>/summary.md` and the two agents' commits, then adjust prompts or checks based on what the real agents got wrong. When the Gemini CLI is installed, add it as the `implement` role (`gemini -p {prompt}` with its auto-approve flag) and rerun.

## Session log
- 2026-09-29T14:39:07Z ClaudeC-Fable: Agreed the design with Alex, wrote `docs/orchestration.md`, blocked on the permission classifier before any script was created.
- 2026-09-29T14:55:00Z ClaudeC-Fable: Alex asked to create the script in the project. Built driver, role table and prompts; tested dry run and two stop paths in a scratch clone; classifier blocked a committing stand-in agent for the remaining paths.

## Outcome
<!-- Filled in only when archiving. -->
