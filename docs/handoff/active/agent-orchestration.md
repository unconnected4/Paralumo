---
task: agent-orchestration
title: Automate the implement/review loop between agents
status: blocked
branch: main
started: 2026-09-29T14:39:07Z
last_updated: 2026-09-29T14:39:07Z
last_commit: 8dcc82b
agents: [ClaudeC-Fable]
related_decisions: [docs/orchestration.md, AGENTS.md]
---

# Automate the implement/review loop between agents

## Goal and acceptance
A script that runs agents in a fixed implement ↔ review loop for one task, so Alex no longer triggers each agent by hand. Design: `docs/orchestration.md`. Done when `scripts/orchestrate/orchestrate.ps1 -Task <slug>` runs at least one implement round and one review round of a real task end to end, stops on the documented conditions, and its logs land in `AgentsOutput/orchestrator/`.

## Current state
Design agreed with Alex and written to `docs/orchestration.md`. Nothing under `scripts/orchestrate/` exists yet: the auto-mode permission classifier in Claude Code refused to create the role config and prompt templates because they set up agents that run with permission checks bypassed. Alex has to unblock this (see Open questions). Until then the design doc is the spec to build from.

## Done
- Design discussed and recorded in `docs/orchestration.md`, this handoff opened.

## Tried and rejected
- Agent triggers the next agent itself (Alex's first framing): rejected in favour of an external driver loop, because a crashed or forgetful agent silently breaks the chain and a hook firing inside a live session is fragile. Alex agreed.
- Agent chooses its successor: rejected for v1; the agent reports `status`, the driver's pipeline table routes.
- Multi-line prompt on the command line: rejected because PowerShell 5.1 quoting of native-command arguments with newlines and quotes is unreliable; the driver writes the rendered prompt to a file and passes a one-line pointer instead.

## Decisions made during this task
- Fixed implement ↔ review pipeline for v1 (by Alex): enough for now.
- Headless runs with auto-approved permissions inside the repo (by Alex): unattended agents cannot answer prompts.
- Gemini CLI will be installed by Alex; until then all roles run Claude Code.
- Driver in PowerShell 5.1 with a JSON role table: matches the host shell and needs no extra runtime.

## Open questions
- For Alex: the Claude Code auto-mode classifier blocks creating `scripts/orchestrate/roles.json` and the prompt templates ("Create Unsafe Agents"), because they configure `claude -p --dangerously-skip-permissions`. Options: add a permission rule for writes under `scripts/orchestrate/` in `.claude/settings.json`, run that session with permissions approved manually, or create the files yourself from `docs/orchestration.md`. This blocks building the driver.
- For Alex: agent names for the headless roles. A headless Claude launched by the driver is a different instance from the interactive `ClaudeC-Fable` session, and the Gemini CLI agent needs a name too. Until assigned, the config would reuse `ClaudeC-Fable` for both roles, which makes `git log --oneline` less informative.
- Next agent: should a new `For Alex:` line stop the loop even when the agent keeps `status: in-progress`? Design says no (report at the end); revisit if agents keep working past questions that should have blocked them.

## Next step
Once Alex unblocks file creation: build `scripts/orchestrate/orchestrate.ps1`, `roles.json` and `prompts/` exactly as `docs/orchestration.md` describes, test the stop paths with a fake role command (e.g. `cmd /c echo`) that makes no commit, then run one real implement + review round on `step1-backend`.

## Session log
- 2026-09-29T14:39:07Z ClaudeC-Fable: Agreed the design with Alex, wrote `docs/orchestration.md`, blocked on the permission classifier before any script was created.

## Outcome
<!-- Filled in only when archiving. -->
