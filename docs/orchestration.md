# Agent orchestration

How agents are chained without a human triggering each one. Driver: `scripts/orchestrate/orchestrate.ps1`. Task history: `docs/handoff/active/agent-orchestration.md`.

## Shape (by Alex)
- 2026-09-29: agents run **headless with auto-approved permissions** inside the repo. Nobody can answer a prompt during a run, so an agent that needs input writes the question into the handoff.
- 2026-10-26: the pipeline is **several implementers in turn, then one reviewer**. Implementation runs Opus, then Gemini 3.7, then GPT-6.1 Sol, repeating the sequence "until none of the agents has anything to do for the current task". Then Fable reviews: if it has comments, they are recorded and the implementation cycle runs again. If there are questions to Alex, Fable tries to resolve them and forwards to Alex only what it can't.
- Other CLIs are added as a row in the role config, not as code.

## Principles
1. **The driver triggers agents, never the other way round.** A finishing agent that calls the next one silently breaks the chain when it crashes, forgets or runs out of budget. A loop outside the agents starts a role, waits for it to exit, checks the result, and starts the next one.
2. **Agents report status; the driver owns routing.** The agent's only signal is what the handoff protocol already requires: the front matter `status`, the `Next step` section and a clean commit. Who runs next is decided by the driver from the pipeline in its config.
3. **A commit is the completion signal.** Everything an agent wants the next one to know is in git: the handoff file and the commit messages. The driver never parses agent output for meaning; the output is only logged.
4. **Stop early rather than loop.** Unattended loops burn budget and can reverse each other's work. The driver stops on the conditions below and reports to Alex.

## Layout
```
scripts/orchestrate/
  orchestrate.ps1        driver (PowerShell 5.1, the host shell on Alex's machine)
  roles.json             pipeline (implementer order, reviewer), limits, one entry per role
  prompts/
    _common.md           rules shared by all roles (name, task, handoff, headless rules)
    implement.md         implementer instructions
    review.md            reviewer instructions
AgentsOutput/orchestrator/<UTC ts>-<task>/   per-run logs and rendered prompts (git-ignored)
```

`roles.json` has three parts:
- `pipeline`: `implementers` (ordered list of role names) and `reviewer` (one role name).
- `limits`: `maxRounds` (whole run), `maxImplementationRoundsPerCycle` (forces a review when implementers never agree they are finished), `roundTimeoutMinutes`.
- `roles.<name>`: `agent` (the agent name used in commits and handoffs), `command`, `model`, `args` and `prompt` (template path). `args` holds the CLI's own flags with two placeholders the driver fills: `{prompt}` (the one-line pointer to the rendered prompt) and `{model}` (the role's `model`). The model flag stays in `args` because each CLI spells it differently: `claude -p {prompt} --model {model} --dangerously-skip-permissions`, `gemini -p {prompt} -m {model} --yolo`, `codex exec --model {model} --dangerously-bypass-approvals-and-sandbox {prompt}`. A role without a `model` field must not reference `{model}`; the driver refuses to start otherwise. The chosen model is printed in every round's summary line.

Current roles: `opus` (Claude Code, `claude-opus-5-5`), `gemini` (Gemini CLI, `gemini-3.7-pro`), `gpt` (Codex CLI, `gpt-6.1-sol`) as implementers in that order, `fable` (Claude Code, `claude-fable-5-1`) as reviewer. The Gemini and Codex rows are configured but unverified: neither CLI is installed yet, and their model ids and flags need checking against the installed versions.

## Statuses
The handoff front matter `status` is the agents' only control signal:
- `in-progress`: work was done and more remains (implementer), or findings were recorded (reviewer).
- `ready-for-review`: an implementer found nothing left to do for the task. Only meaningful in the implementation phase.
- `blocked`: the agent can't continue without Alex; the question is in Open questions tagged `For Alex:`.
- `done` / `abandoned`: set by the reviewer only, who then archives the handoff.

## One round
1. Record `HEAD` and require a clean working tree.
2. Render the role's prompt template with `{agent}`, `{task}`, `{handoff}`, `{round}`, `{cycle}`, `{implementers}` and `{notes}` (what the driver wants the agent to know about the previous round) and save it to the run folder. The agent is started with a one-line prompt that points at that file; this avoids shell quoting of multi-line prompts and works the same for every CLI.
3. Run the command with stdout/stderr captured to the run folder, with a timeout.
4. Protocol checks, the same for every role. Any failure stops the loop with a reason:
   - **Non-zero exit code.**
   - **Uncommitted changes** → the agent did not finish per protocol.
   - **`HEAD` did not move** → no commit, nothing to hand on.
   - **Handoff file not part of the round's commits** → the next agent would start from stale context. (If the file was `git mv`ed to `docs/handoff/archive/` in this round, the task counts as done.)
   - `last_commit` in the front matter must resolve and be at or after the round's starting `HEAD`; otherwise warn and pass the warning into the next prompt via `{notes}`.
5. Route on `status` (next section).

## Routing
**Implementation phase.** Implementers run in the configured order, wrapping around. The driver keeps a streak of consecutive implementers that reported nothing to do:
- `in-progress` with changes outside `docs/handoff/` resets the streak. `in-progress` without any such change counts as nothing to do (and is noted), so an agent that doesn't use `ready-for-review` can't keep the loop spinning.
- `ready-for-review` increments the streak. `done` from an implementer is treated the same, with a warning.
- When the streak reaches the number of implementers, every agent has seen the task in its latest state and found nothing: the reviewer runs.
- `blocked` from an implementer sends the task to the reviewer at once, with a note to try resolving the question.
- If `maxImplementationRoundsPerCycle` rounds pass without a full streak, the reviewer runs anyway, with a note that the cap was hit.

**Review phase.** The reviewer first tries to resolve open `For Alex:` questions from `intent.md`, `docs/`, history and code, appending `→ <answer> (resolved by <agent>, <date>: <evidence>)` to the line; it never uses the `by Alex` tag for its own answers. Then it reviews and sets:
- `done` or `abandoned` → the loop stops successfully (the reviewer archives the handoff per `AGENTS.md`).
- `in-progress` → findings are at the top of `Next step`; a new implementation cycle starts with the first implementer.
- `blocked` → the loop stops; Alex reads the handoff.

**Final report.** The run summary ends with the stop reason, the commit range, every still-open `For Alex:` question in the handoff, and every `For Alex:` line added during the run.

## Prompts
The templates are short because `AGENTS.md` already tells every agent how to start and end a session. They add: the agent's name and role, the task slug and handoff path, the list of implementers, the headless rules (never ask, never wait, write questions into the handoff, only Alex's answers carry `by Alex`), and the role-specific status rules:
- **Implementer**: work through `Next step`, address review findings first, record disagreement instead of ignoring or silently reverting another agent's work, don't invent work when nothing is left, run the project's checks, then set `in-progress`, `ready-for-review` or `blocked`. Commit even when only the handoff changed.
- **Reviewer**: resolve open questions first, review the task's commits since the last review, re-run the checks, check that decisions are recorded and nothing was undone without reason, commit small unambiguous fixes directly, record larger findings as a numbered list at the top of `Next step`, then set `in-progress`, `blocked` or `done`.

## Running
```
powershell -ExecutionPolicy Bypass -File scripts/orchestrate/orchestrate.ps1 -Task step1-backend
```
`-ExecutionPolicy Bypass` applies to that one process only; the script is unsigned and the default policy refuses it. Other switches: `-DryRun` renders the first prompt and prints the command without running an agent, `-StartWith <role>` starts with that implementer or with the reviewer, `-MaxRounds N` and `-Config <path>` override the config.

Requirements: a clean working tree, an existing `docs/handoff/active/<task>.md` with Goal and Next step filled in (by a human or an earlier session), and the CLIs from `roles.json` on `PATH`. A missing CLI fails the round that needs it, so until Gemini and Codex are installed, run with a config that lists only the installed roles.

## Limits of the first version
- Sequential only. Parallel roles would need one worktree per agent; not planned until there is a reason.
- Only agents with a CLI can be triggered. Desktop-app agents (Cowork, Antigravity) stay manual; the driver's answer for them is "stop and tell Alex".
- No pushing. The driver works on the local branch; Alex pushes.
- A killed round (timeout) kills the CLI process only. A `.cmd` shim such as npm's `gemini` may leave its child running.

## Testing the driver
Stop paths are tested by pointing `-Config` at a copy of `roles.json` whose role commands are stand-ins, run in a scratch clone so no test commit lands in the real history. Verified so far: dry run for an implementer and for the reviewer, `-StartWith` validation, a role that exits without committing (`cmd.exe /c echo`), a role that exits non-zero (`cmd.exe /c exit 3`), the open-questions report. Not yet exercised with a real or stand-in committing agent: the streak logic, the per-cycle cap, the implementer `blocked` hand-over, the reviewer's `done` / `in-progress` / `blocked` outcomes, the stale `last_commit` warning and the archived-handoff detection. See the handoff's Next step.
