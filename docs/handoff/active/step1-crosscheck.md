---
task: step1-crosscheck
title: Cross-check of the Step 1 backend by all three implementers, then review
status: in-progress
branch: main
started: 2026-11-06T12:24:36Z
last_updated: 2026-11-06T12:24:36Z
last_commit: fd097c1
agents: [ClaudeC-Fable]
related_decisions: [docs/plan.md, intent.md, backend/intent.md, backend/AGENTS.md, web/intent.md, docs/engineering.md, docs/api/openapi.json, docs/handoff/archive/handoff-20261106T105500Z.md]
---

# Cross-check of the Step 1 backend by all three implementers, then review

## Goal and acceptance
The Step 1 backend was written by one implementer (`ClaudeC-Opus5.5`, commit 27cb309) and reviewed by one reviewer (`ClaudeC-Fable`, b74cc11), both Claude models. Neither `AgyC-Gemini4Argon` nor `CodexC-GPT5.6Terra` has looked at it. Alex wants all three implementers to review the code and fix their findings, and only then Fable to review again (by Alex, 2026-11-06: "I want that all three reviewed the code, fix findings and only then Fable reviews it again").

**In this task, reviewing is the work.** The role prompt says "do not invent work"; that does not apply to your own review here. Your review is outstanding work until your row in "Review coverage" below is filled in.

Scope, i.e. everything Step 1 shipped:
- `backend/`: all of it (build files, `src/main`, `src/test`, migration, fake data, `.env.example`).
- `docs/api/openapi.json`, exported from the backend.
- The web side of the contract: `web/src/api/` (`client.ts`, `types.ts`, generated `schema.d.ts`), `web/src/mocks/handlers.ts`, and the dependency changes in `web/package.json`.

Review against: `docs/plan.md` (Step 1, and Step 2 as the next consumer of the provider interface), `intent.md`, `backend/intent.md`, `backend/AGENTS.md`, `web/intent.md`, `docs/engineering.md`, and the contract summary in the archived Step 1 handoff (`docs/handoff/archive/handoff-20261106T105500Z.md`, read-only).

What to look for, at least:
- **Correctness**: each endpoint against the contract (status codes, bodies, the 422 error shape), edge cases (empty and odd queries, unknown ids, saving the same sense twice, restore after soft delete, concurrent requests).
- **Security**: password hashing, session token creation and validation, cookie attributes, the recorded no-CSRF-token decision and whether its preconditions hold, CORS, what error responses leak, account enumeration on register and login.
- **Per-user isolation**: every endpoint that touches user data, and whether a test proves it (`backend/AGENTS.md` requires one each).
- **Persistence**: migration against entities, constraints and indexes, transaction boundaries, behaviour under a unique-constraint race.
- **Provider boundary**: whether `DictionaryProvider` and the sense id scheme will take the Yandex provider in Step 2 without reshaping callers.
- **Configuration**: env vars read only through `@ConfigurationProperties`, `.env.example` complete, no secrets anywhere.
- **Tests**: gaps, tests that would pass with broken code, anything order-dependent or time-dependent.
- **Contract drift**: spec against controllers and DTOs, generated client against spec, MSW handlers against spec.
- **Build**: the no-warnings rule (`docs/engineering.md`), pinned versions, anything suppressed instead of fixed.

Acceptance:
1. Each of the three implementers has done its own full review of the scope and recorded it in "Review coverage".
2. Every finding is in "Findings" with a status: `fixed <sha>`, `rejected` (with the reason) or `proposal` (see rule 3 below). None is left `open`.
3. Backend `./gradlew check` passes with no warnings, and web `npm run lint && npm run typecheck && npm test && npm run build` passes; `npm run gen:api` leaves no diff.
4. Fable's review, which runs after the implementers stop, has no findings.

Rules for this task:
1. **Review independently first.** Form your own findings from the code before reading the "Findings" list, then merge: add what is new, and add your name to findings another agent already recorded. An independent second opinion is the point of this task.
2. **Fix defects, with a test.** A defect is wrong behaviour, a security weakness, a contract mismatch, a missing required test, or a broken rule from an `AGENTS.md`. Fix it in the same round, add or extend a test that fails without the fix where that is possible, and commit the fix with the reason in the body. One commit per finding or per closely related group, not one large commit: the previous task's retrospective asked for smaller steps.
3. **Don't change recorded decisions; propose instead.** If a finding disagrees with a decision in `intent.md`, `backend/intent.md`, `web/intent.md` or `docs/`, leave the code alone and record the finding with status `proposal` and your argument. Items tagged `by Alex` are settled. Matters of taste (naming, layout, style the formatter accepts) are not findings.
4. **Disagreeing with another agent's fix**: don't revert it. Record your objection on that finding's line and under "Tried and rejected"; the reviewer decides.
5. **API changes** update the controller or DTO, `docs/api/openapi.json` (`./gradlew exportOpenApi`) and the web client (`npm run gen:api`) in the same commit (root `AGENTS.md`).
6. **Later rounds are narrow.** Once your row in "Review coverage" is filled, a later round of yours only reads the commits made since your last round, checks those changes, and records or fixes defects in them. Don't start a second full review.

When to set which status (this refines the role prompt for this task):
- `in-progress`: you changed anything outside `docs/handoff/` in this round.
- `ready-for-review`: your row in "Review coverage" is filled (in this round or an earlier one), you have read every commit since your last round, and you made no change outside `docs/handoff/` in this round. A first review that finds nothing ends with this status, with the row filled in.
- `blocked`: the checks can't be run on this machine or you need Alex; record the `For Alex:` question.

## Review coverage
Fill in your own row only, in the round where you do your full review.

| Agent | Full review done (UTC date, round) | Findings recorded | Reviewed at commit |
|---|---|---|---|
| ClaudeC-Opus5.5 | | | |
| AgyC-Gemini4Argon | | | |
| CodexC-GPT5.6Terra | | | |

Opus wrote this code. For Opus, a full review means re-reading it as a reviewer would, against the list above, not confirming that the checks still pass.

## Findings
One line per finding, numbered `F1`, `F2`, … in the order recorded. Never renumber or delete a line; update its status.

Format: `- F<n> [defect|improvement|proposal] <file or area>: <what is wrong and why it matters>. Found by: <agents>. Status: open | fixed <sha> | rejected (<reason>, <agent>) | proposal.`

- None yet.

## Current state
Not started. The code under review is at `fd097c1`; nothing in scope has changed since the Step 1 review (b74cc11). All checks passed at that commit.

## Done
- Nothing yet.

## Tried and rejected
- Reopening the Step 1 handoff for this: rejected, archived handoffs are read-only (`AGENTS.md`), and a cross-check has its own goal and acceptance.

## Decisions made during this task
- All three implementers review and fix, then Fable reviews (by Alex, 2026-11-06): "I want that all three reviewed the code, fix findings and only then Fable reviews it again".
- A per-agent coverage table and status rules tied to it (ClaudeC-Fable, 2026-11-06): the driver starts the review when all implementers in a row report nothing to do, so without a record of who has reviewed, an implementer could pass its turn without reviewing.

## Open questions
- Next agent: local setup on this machine. There is no Docker and no `java` on `PATH`, and `JAVA_HOME` is not set. A portable JDK 25 and PostgreSQL 16 live in `AgentsOutput/ClaudeC-Opus5.5/`: set `JAVA_HOME` in your own shell to `<repo>/AgentsOutput/ClaudeC-Opus5.5/jdk/jdk-25.0.5+8` before calling `./gradlew`. PostgreSQL was accepting connections on `localhost:5432` when this file was written, with the same users and databases as `docker-compose.yml`; if it is down, start it from that folder with `pg/pgsql/bin/pg_ctl -D pgdata -l pg.log start`. `backend/.env` exists. Use that folder, don't edit files in it (it belongs to another agent), and don't write its paths into repo files other than this handoff.
- Next agent: tests and local runs use the fake dictionary provider only; never call the live Yandex API (`AGENTS.md`).

## Next step
1. `ClaudeC-Opus5.5`, `AgyC-Gemini4Argon`, `CodexC-GPT5.6Terra`, each in your first round: do your full review of the scope in "Goal and acceptance" (rule 1), record findings, fix defects with tests (rule 2), run the backend and web checks, fill in your row in "Review coverage", update this handoff and commit.
2. In any later round: follow rule 6 and the status rules above.
3. Reviewer (`ClaudeC-Fable`), when the implementers have stopped: check the acceptance list. If a row in "Review coverage" is empty, set `in-progress` and say which agent still owes its review. Decide every `proposal` and every recorded disagreement, review the fix commits, re-run all checks. Copy accepted proposals that change a decision into the matching `intent.md` before closing.

## Session log
- 2026-11-06T12:24:36Z ClaudeC-Fable: Opened this handoff at Alex's request after confirming from history and run logs that Gemini and Codex never worked on Step 1. No code changed.

## Outcome
<!-- Filled in only when archiving. -->
