# Paralumo: agent instructions

Shared instructions for every coding agent working on this repo (Claude, Codex, Copilot, Cursor, Gemini, …). Tool-specific files such as `CLAUDE.md` should only import or point to this file and add notes that apply to that tool alone.

Vocabulary app: dictionary lookup with per-sense saving and spaced-repetition practice. English→Russian is only the first language pair, used to stabilise the project skeleton. Product intent and build order: [intent.md](intent.md).

## Repo map
- `backend/`: Spring Boot (Java) JSON API. Commands and rules: [backend/AGENTS.md](backend/AGENTS.md)
- `web/`: React + Vite + TypeScript SPA. Commands and rules: [web/AGENTS.md](web/AGENTS.md)
- `e2e/`: Playwright tests that run against web and backend together
- `docs/`: shared docs covering the plan, engineering conventions, API contract and decisions. Agent chaining: [docs/orchestration.md](docs/orchestration.md)
- `scripts/orchestrate/`: driver that runs agents in an implement/review loop. How and why: [docs/orchestration.md](docs/orchestration.md)
- `docker-compose.yml`: local infrastructure only (Postgres)
- `.github/workflows/`: one pipeline per project, filtered by path

## Before you start
- Read the `AGENTS.md` of the project you are changing. Read `intent.md` (root and project) before making a design decision.
- Check `docs/engineering.md` for how to run, test, configure and deliver anything.

## Global rules
- `backend` and `web` are built, tested and delivered independently. They meet only at the API contract in `docs/api/openapi.json`. Any API change updates the spec and the web client in the same commit.
- Decisions that affect both projects go in the root `intent.md` or in `docs/`. Project-specific ones go in that project's `intent.md`. If you make or change a decision, record it there. Don't leave it only in code or chat.
- Never commit secrets. Configuration comes from environment variables, as described in [docs/engineering.md](docs/engineering.md#configuration-and-secrets).
- Tests and local runs use the fake dictionary provider. Never call the live Yandex API from tests or CI.
- A change is done when its project's lint, type checks and tests pass locally, using the commands in that project's `AGENTS.md`.
- Keep changes scoped. Don't reformat, rename or "tidy" code unrelated to the task.
- Keep instruction files lean: global rules here, project detail in the project's `AGENTS.md`.

## Agent names and output folders
- Each agent has a stable name, assigned by the repo owner, used in commit trailers, handoff files and its output folder. Known agents:
  - `ClaudeD-Fable5.1`: Claude (Cowork desktop)
  - This list is incomplete on purpose. It will be updated once agent automation is built, so don't ask about or add missing names until then (by Alex).
- `AgentsOutput/<agent name>/` is each agent's scratch space for drafts, generated files and anything not ready for the repo. The whole folder is git-ignored. Write only in your own subfolder, and treat other agents' folders as read-only.
- Nothing in `AgentsOutput/` is a source of truth. Anything that matters must be moved into the repo proper or recorded in a handoff file.

## Working with Alex
Alex is the repo owner: a human who adds context when agents are stuck or going in circles.
- **Mark Alex's input.** Record any clarification or decision from Alex with the tag `by Alex`, wherever it ends up (handoff file, `intent.md`, `docs/`, commit body), e.g. `- Keep MSW for Step 0 (by Alex): <reason as Alex gave it>`. Treat tagged items as settled. Don't reopen them without new evidence, and if you have some, ask Alex instead of overriding.
- **Ask for context instead of guessing.** Sometimes progress depends on something only Alex knows (intent, priorities, trade-offs, accounts, taste), or agents keep reversing each other's work. In that case, add the question to the handoff's Open questions tagged `For Alex:` and repeat it at the end of your chat reply. Meanwhile, carry on with anything it doesn't block.
- Both tags are fixed strings. `git grep "For Alex:"` lists what is waiting on Alex, and `git grep "by Alex"` lists Alex's answers.

## Handoff protocol
Several different agents work on this repo, and none of them remembers previous sessions. Reasoning is passed on through handoff files and commit messages. Details and the file layout: [docs/handoff/README.md](docs/handoff/README.md).

Use a handoff file for any task that spans more than one commit or session. A self-contained single-commit change only needs a good commit message.

**Starting a session**
1. Look in `docs/handoff/active/` for the task's file. If there isn't one, copy `docs/handoff/TEMPLATE.md` to `docs/handoff/active/<task-slug>.md`.
2. Read it, then catch up on everything since it was last updated: `git log --format=full <last_commit>..HEAD` (add `-p` for diffs).

**Ending a session (always, even if the work is unfinished)**
3. Update Current state, Done, Tried and rejected, Decisions, Open questions and Next step. Append one line to Session log. Set `last_updated` and `last_commit`, and add your agent name to `agents`.
4. Commit the handoff file together with the work.

**Finishing a task (done or abandoned)**
5. Set `status`, fill in Outcome, and copy any lasting decisions into `intent.md` or `docs/`.
6. Archive it; never delete it: `git mv docs/handoff/active/<task-slug>.md docs/handoff/archive/handoff-<UTC timestamp>.md` (timestamp format `YYYYMMDDTHHMMSSZ`). Archived files are read-only.

Record distilled reasoning (decisions, rejected options, evidence), not raw transcripts or chain-of-thought. Never paste secrets.

## Commit messages
- Subject line: `[<agent name>] <what changed>`, e.g. `[ClaudeD-Fable5.1] Add Yandex response mapping`. The prefix lets `git log --oneline` show who made each commit, and who committed last.
- Body: **why**, including alternatives considered when it isn't obvious. Bodies can be as long as needed.
- End every commit with trailers:
  ```
  Task: <task-slug>            # omit for standalone commits
  Agent: <agent name>          # e.g. ClaudeD-Fable5.1
  ```
- To find what other agents did since your last work: `git log --grep="Agent: <your agent name>" -1 --format=%H`, then `git log --format=full <that sha>..HEAD`.
- Merge with rebase or merge commits. Don't squash: squashing loses the per-commit reasoning.
