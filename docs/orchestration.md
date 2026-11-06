# Agent orchestration

How agents are chained without a human triggering each one. Driver: `scripts/orchestrate/orchestrate.ps1`. Task history: `docs/handoff/active/agent-orchestration.md`.

## Shape (by Alex)
- 2026-09-29: agents run **headless with auto-approved permissions** inside the repo. Nobody can answer a prompt during a run, so an agent that needs input writes the question into the handoff.
- 2026-10-26: the pipeline is **several implementers in turn, then one reviewer**. Implementation runs Opus, then Gemini (Gemini 3.7 at the time; switched to Gemini 4 Argon via the Antigravity CLI on 2026-10-28, by Alex), then GPT-5.6 Terra (by Alex, 2026-10-28), repeating the sequence "until none of the agents has anything to do for the current task". Then Fable reviews: if it has comments, they are recorded and the implementation cycle runs again. If there are questions to Alex, Fable tries to resolve them and forwards to Alex only what it can't.
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

`roles.json` has four parts:
- `pipeline`: `implementers` (ordered list of role names) and `reviewer` (one role name).
- `limits`: `maxRounds` (whole run), `maxImplementationRoundsPerCycle` (forces a review when implementers never agree they are finished), `roundTimeoutMinutes`.
- `env` (optional): environment variables the driver sets for itself, so every agent process inherits them. They are printed in the run summary. Currently `GRADLE_OPTS=-Dorg.gradle.daemon=false`; see "A round that finishes but does not exit".
- `roles.<name>`: `agent` (the agent name used in commits and handoffs), `command`, `model`, `args` and `prompt` (template path). `args` holds the CLI's own flags with two placeholders the driver fills: `{prompt}` (the one-line pointer to the rendered prompt) and `{model}` (the role's `model`). The model flag stays in `args` because each CLI spells it differently: `claude -p {prompt} --model {model} --dangerously-skip-permissions`, `agy -p {prompt} --model {model} --dangerously-skip-permissions`, `codex exec --model {model} --dangerously-bypass-approvals-and-sandbox {prompt}`. A role without a `model` field must not reference `{model}`; the driver refuses to start otherwise. The chosen model is printed in every round's summary line.

Current roles and agent names: `opus` (Claude Code, `claude-opus-5-5`, agent `ClaudeC-Opus5.5`), `gemini` (Antigravity CLI `agy`, `gemini-4-argon-high`, agent `AgyC-Gemini4Argon`), `gpt` (Codex CLI, `gpt-5.6-terra`, agent `CodexC-GPT5.6Terra`) as implementers in that order, `fable` (Claude Code, `claude-fable-5-1`, agent `ClaudeC-Fable`) as reviewer. The names follow the convention in `AGENTS.md` ("Agent names and output folders"), which is the list of record; a name identifies tool + model and is shared by interactive sessions using the same (by Alex, 2026-10-28). The `agy` flags and model ID have been verified end-to-end. Codex CLI version 0.164.0 accepted the `exec`, `--model`, and `--dangerously-bypass-approvals-and-sandbox` flags; a read-only invocation verified `gpt-5.6-terra` with the signed-in account on 2026-10-28.

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
`-ExecutionPolicy Bypass` applies to that one process only; the script is unsigned and the default policy refuses it. Other switches: `-DryRun` renders the first prompt and prints the command without running an agent, `-AdoptUncommitted` starts on a dirty tree and hands the leftovers to the first round (see "Recovering from an interrupted round"), `-StartWith <role>` starts with that implementer or with the reviewer, `-MaxRounds N` and `-Config <path>` override the config.

Requirements: a clean working tree, an existing `docs/handoff/active/<task>.md` with Goal and Next step filled in (by a human or an earlier session), and the CLIs from `roles.json` on `PATH`. A missing CLI fails the round that needs it.

## Recovering from an interrupted round
A round that does not finish leaves the agent's work uncommitted: the driver was stopped (Ctrl+C, closed terminal, sleep, reboot) or the round hit its timeout and was killed. The next start then finds a dirty working tree and refuses to run, because it cannot tell an agent's leftovers from Alex's own edits. Its error message lists the changed paths and, when the task's last real run folder has a summary without a `## Result` section, names that run and the round it was interrupted in.

Two ways forward:
- **Adopt the work** (the usual choice): rerun with `-AdoptUncommitted`. The driver starts anyway and tells the first round, through `{notes}`, that the tree holds uncommitted changes from a round that did not finish: inspect them, decide whether they are sound, finish and verify the work, commit everything per protocol, and record in the handoff what was found and done. Start with the role that was interrupted (`-StartWith <role>` if it isn't first), so the agent picks up its own work and the commit carries the right name. All normal checks still apply at the end of that round.
- **Set it aside**: `git stash -u` (or commit it yourself), then run normally.

First seen on 2026-10-28: an Opus round on `step1-backend` was interrupted after about half an hour with the backend scaffold, passing tests and the regenerated web client in the tree, but no handoff update and no commit.

## A round that finishes but does not exit
Seen on 2026-11-06 in round 3 of `step1-crosscheck`: Codex committed its work and the handoff and printed its final message 11 minutes into the round, but `codex.exe` stayed alive until the driver killed it at the 90-minute timeout, which stops the run. Nothing was lost; the tree was clean and the run could be resumed with `-StartWith opus`.

Most likely cause, not proven: Codex had started Gradle daemons (three `./gradlew check` at once), and one was still running, idle, when the round was killed. Gradle daemons outlive the build by design (three hours idle), and `codex exec` appears not to exit while a process it started is alive. The Claude and Antigravity CLIs exited normally with a daemon left behind. Evidence: Codex's rounds without Gradle (2026-10-28) exited at once; the daemon started by this round (13:50) was still alive after the kill.

Countermeasure: `roles.json` → `env` sets `GRADLE_OPTS=-Dorg.gradle.daemon=false` for all agents. Gradle then forks a single-use daemon that stops at the end of each build (checked: no `java` process remains afterwards, and the build prints no warning). Builds start a few seconds slower. Developers' own shells are unaffected. If a Codex round hangs again with no leftover process, the cause is something else: look at what is still running under `codex.exe` before the timeout.

## Limits of the first version
- Sequential only. Parallel roles would need one worktree per agent; not planned until there is a reason.
- Only agents with a CLI can be triggered. Desktop-app agents without a CLI (such as Cowork) stay manual; the driver's answer for them is "stop and tell Alex".
- No pushing. The driver works on the local branch; Alex pushes.
- A killed round (timeout) runs `taskkill /T /F` on the CLI process ID so child processes of `.cmd` shims are also terminated.

## Testing the driver
Stop paths are tested by pointing `-Config` at a copy of `roles.json` whose role commands are stand-ins, run in a scratch clone so no test commit lands in the real history. Verified so far: dry run for an implementer and for the reviewer, `-StartWith` validation, a role that exits without committing (`cmd.exe /c echo`), a role that exits non-zero (`cmd.exe /c exit 3`), the open-questions report, the dirty-tree hint and `-AdoptUncommitted`.

Verified by real runs: a handoff-only implementer round counted as nothing to do (Codex, 2026-10-28); and on 2026-11-06 the first complete run, on `step1-backend` with Opus as the only implementer: `ready-for-review` → streak → review, the reviewer resolving a `For Alex:` question, setting `done` and archiving, and the driver detecting the archived handoff and stopping successfully. Not yet exercised: the streak with three implementers, the per-cycle cap, the implementer `blocked` hand-over, the reviewer's `in-progress` and `blocked` outcomes, and the stale `last_commit` warning. See the handoff's Next step.

The driver reads repo files and git output as UTF-8 explicitly. PowerShell 5.1 otherwise decodes files as ANSI and native output with the console's OEM code page, which garbled `→` and `✓` in the first real run's prompt and summary.
