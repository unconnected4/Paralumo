# Agent orchestration

How agents are chained without a human triggering each one. Driver: `scripts/orchestrate/orchestrate.ps1`. Task history: `docs/handoff/active/agent-orchestration.md`.

## Shape (by Alex, 2026-09-29)
- A fixed **implement ↔ review** pipeline is enough for the first version. Per-task routing comes later if needed.
- Agents run **headless with auto-approved permissions** inside the repo. Nobody can answer a prompt during a run, so an agent that needs input has to stop and write the question into the handoff.
- Alex will install the **Gemini CLI**; until then every role resolves to Claude Code. Other CLIs are added as a row in the role config, not as code.

## Principles
1. **The driver triggers agents, never the other way round.** A finishing agent that calls the next one silently breaks the chain when it crashes, forgets or runs out of budget. Instead a loop outside the agents starts a role, waits for it to exit, checks the result, and starts the next role.
2. **Agents report status; the driver owns routing.** The agent's only signal is what the handoff protocol already requires: the front matter `status` (`in-progress | blocked | done | abandoned`), the `Next step` section and a clean commit. Which role runs next is a table in the driver's config.
3. **A commit is the completion signal.** Everything an agent wants the next one to know is in git: the handoff file and the commit messages. The driver never parses agent output for meaning; the output is only logged.
4. **Stop early rather than loop.** Unattended loops burn budget and can reverse each other's work. The driver stops on the conditions below and reports to Alex.

## Layout
```
scripts/orchestrate/
  orchestrate.ps1        driver (PowerShell 5.1, the host shell on Alex's machine)
  roles.json             pipeline table, limits, and one entry per role
  prompts/
    _common.md           rules shared by all roles (name, task, handoff, headless rules)
    implement.md         implementer instructions
    review.md            reviewer instructions
AgentsOutput/orchestrator/<UTC ts>-<task>/   per-run logs and rendered prompts (git-ignored)
```

`roles.json` has three parts:
- `pipeline`: `start` role and an `after` map (`implement → review`, `review → implement`).
- `limits`: `maxRounds`, `maxRoundsWithoutCodeChange`, `roundTimeoutMinutes`.
- `roles.<name>`: `agent` (the agent name used in commits and handoffs), `command`, `args` (with a `{prompt}` placeholder) and `prompt` (template path). A Claude role calls `claude -p` with permission checks bypassed; a Gemini role calls `gemini -p` with its auto-approve flag.

## One round
1. Record `HEAD` and require a clean working tree.
2. Render the role's prompt template with `{agent}`, `{task}`, `{handoff}`, `{round}` and save it to the run folder. The agent is started with a one-line prompt that points at that file; this avoids shell quoting of multi-line prompts and works the same for every CLI.
3. Run the command with stdout/stderr captured to the run folder, with a timeout.
4. Post-checks, in order. Any failure stops the loop with a reason:
   - **Uncommitted changes** → the agent did not finish per protocol.
   - **`HEAD` did not move** → no commit, nothing to hand on.
   - **Handoff file not part of the round's commits** → the protocol was not followed; the next agent would start from stale context. (If the file was `git mv`ed to `docs/handoff/archive/` in this round, the task counts as `done`.)
   - `last_commit` in the front matter must resolve and be at or after the round's starting `HEAD`; otherwise warn, and pass the warning into the next prompt.
5. Read `status` from the handoff front matter:
   - `done` or `abandoned` → stop, success.
   - `blocked` → stop, Alex is needed.
   - `in-progress` → continue with `pipeline.after[role]`.
6. Count rounds whose commits touched nothing outside `docs/handoff/`. A review round that only records findings is normal; two such rounds in a row means the agents are going in circles → stop.
7. Collect lines added to the handoff that contain `For Alex:` for the final report. They do not stop the loop by themselves (the agent sets `blocked` if they do), but Alex sees them at the end.

## Prompts
The templates are short because `AGENTS.md` already tells every agent how to start and end a session. They add only: the agent's name and role, the task slug and handoff path, the headless rules (never ask, never wait, write questions into the handoff), and the role-specific status rules:
- **Implementer**: work through `Next step`, address review findings first and record disagreement instead of ignoring a finding, run the project's checks, set `in-progress` or `blocked`. Never sets `done`; only the reviewer closes a task.
- **Reviewer**: review the task's commits since the last review, re-run the checks, commit small unambiguous fixes directly, record larger findings as a numbered list at the top of `Next step`, set `in-progress`, `blocked`, or `done`. On `done` it finishes the task per `AGENTS.md` (Outcome, promote decisions, archive the handoff).

## Running
```
powershell -ExecutionPolicy Bypass -File scripts/orchestrate/orchestrate.ps1 -Task step1-backend
```
`-ExecutionPolicy Bypass` applies to that one process only; the script is unsigned and the default policy refuses it. Other switches: `-DryRun` renders the first prompt and prints the command without running an agent, `-StartRole review` starts with a review, `-MaxRounds N` and `-Config <path>` override the config.

Requirements: a clean working tree, an existing `docs/handoff/active/<task>.md` with Goal and Next step filled in (by a human or an earlier session), and the CLIs from `roles.json` on `PATH`. The driver prints a per-round summary and ends with the stop reason, the commit range, and any new `For Alex:` questions.

## Limits of the first version
- Sequential only. Parallel roles would need one worktree per agent; not planned until there is a reason.
- Only agents with a CLI can be triggered. Desktop-app agents (Cowork, Antigravity) stay manual; the driver's answer for them is "stop and tell Alex".
- No pushing. The driver works on the local branch; Alex pushes.
- A killed round (timeout) kills the CLI process only. A `.cmd` shim such as npm's `gemini` may leave its child running.

## Testing the driver
Stop paths are tested by pointing `-Config` at a copy of `roles.json` whose role commands are stand-ins, run in a scratch clone so no test commit lands in the real history. Verified so far: dry run, a role that exits without committing (`cmd.exe /c echo`), a role that exits non-zero (`cmd.exe /c exit 3`). Not yet exercised with a real or stand-in committing agent: the `done`, `blocked`, stale `last_commit` and going-in-circles paths, and the archived-handoff detection. See the handoff's Next step.
