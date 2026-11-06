---
task: step1-crosscheck
title: Cross-check of the Step 1 backend by all three implementers, then review
status: in-progress
branch: main
started: 2026-11-06T12:24:36Z
last_updated: 2026-11-06T12:52:00Z
last_commit: 213751f
agents: [ClaudeC-Fable, ClaudeC-Opus5.5]
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
| ClaudeC-Opus5.5 | 2026-11-06, round 1 | F1–F10 | a2f8522 |
| AgyC-Gemini4Argon | | | |
| CodexC-GPT5.6Terra | | | |

Opus wrote this code. For Opus, a full review means re-reading it as a reviewer would, against the list above, not confirming that the checks still pass.

## Findings
One line per finding, numbered `F1`, `F2`, … in the order recorded. Never renumber or delete a line; update its status.

Format: `- F<n> [defect|improvement|proposal] <file or area>: <what is wrong and why it matters>. Found by: <agents>. Status: open | fixed <sha> | rejected (<reason>, <agent>) | proposal.`

- F1 [defect] `config/SessionTokens`: tokens were issued with the injected `Clock` but their expiry was checked by the decoder's default `JwtTimestampValidator` against the system clock. `SessionTokensTest` issues at a fixed 2026-10-28, so `verifiesItsOwnTokens` would have started failing after 2026-11-27, and `rejectsExpiredTokens` passed only because the system clock was later. Found by: ClaudeC-Opus5.5. Status: fixed cacba92.
- F2 [defect] `api/Dtos.RegisterRequest`: Spring Security 7's BCrypt encoder throws for passwords over 72 bytes, so registering with a long password (or 37+ Cyrillic letters) was a 500. Now 422 `password must be at most 72 bytes` via a byte-counting `@MaxUtf8Bytes` constraint. Login was already fine: `matches` truncates, so a long login password is an ordinary 401. Found by: ClaudeC-Opus5.5. Status: fixed 4f5b2f4.
- F3 [defect] `service/CardService.save`: check-then-insert with no handling of the `(user_id, sense_id)` unique violation, so two concurrent saves of one sense by one user (double click, two tabs) made the second a 500. Now retried once in a fresh transaction, answering 200 with the existing card; deterministic test with a second, uncommitted connection. Found by: ClaudeC-Opus5.5. Status: fixed 14c4f05.
- F4 [improvement] `service/LookupService`: the retry after a concurrent first lookup of the same word was untested. Test added (no code change); it fails if the retry is broken. Found by: ClaudeC-Opus5.5. Status: fixed 3411e2f.
- F5 [defect] `api/ApiExceptionHandler`: exceptions it didn't map (database errors, bugs, the F3 race) fell through to Spring Boot's `/error` body `{timestamp,status,error,path}`, which has no `detail`, against the recorded "errors are ProblemDetail" rule. A catch-all now logs and answers a generic 500 problem body without internals. Found by: ClaudeC-Opus5.5. Status: fixed f768ba5.
- F6 [improvement] CSRF: the no-CSRF-token decision relies on state-changing endpoints rejecting the bodies an HTML form can send; nothing tested that. Test added: `text/plain`, form-urlencoded and multipart posts to login and cards are 415 (holds on current code). Found by: ClaudeC-Opus5.5. Status: fixed 213751f.
- F7 [proposal] Session revocation: logout only clears the cookie. The JWT stays valid for 30 days, so a copied cookie or a bearer token (the planned Android transport) can't be signed out, and a password change couldn't end other sessions. This follows the recorded stateless-JWT decision, so I left the code alone. Proposal: before bearer clients ship (or with account settings), add revocation, e.g. a `token_version` column on `users` checked by `SessionTokenFilter` (one primary-key read per authenticated request), or server-side sessions. Found by: ClaudeC-Opus5.5. Status: proposal.
- F8 [proposal] Logout CSRF: `POST /api/auth/logout` takes no body and needs no cookie, so a cross-site form can sign a user out. The recorded CSRF decision accepts "no body" endpoints, and the only effect is a nuisance logout. Proposal: accept as is and say so in the CSRF note in `backend/intent.md`; requiring a JSON body would change the contract for no real gain. Found by: ClaudeC-Opus5.5. Status: proposal.
- F9 [proposal] Brute force: login and register have no rate limiting or lockout. This isn't in Step 1's plan, and the right mechanism (proxy or app-level) depends on hosting, which is TBD. Proposal: add it to `docs/plan.md` as a pre-production item tied to the hosting decision. Found by: ClaudeC-Opus5.5. Status: proposal.
- F10 [defect] A validly signed token for a user id with no `users` row (local DB reset with the same `AUTH_SECRET`) makes `POST /api/cards` hit the `user_cards.user_id` foreign key: a 500 (a problem body since F5). Found by: ClaudeC-Opus5.5. Status: rejected (nothing deletes users in production; a stale local cookie is cleared by signing in again, since `/api/auth/me` already answers 401 for it; account deletion, when it comes, needs revocation anyway, see F7. ClaudeC-Opus5.5).

## Current state
`ClaudeC-Opus5.5` has done its full review (round 1): F1–F10. It fixed four defects and closed two test gaps in cacba92..213751f, and left three proposals and one rejection. `AgyC-Gemini4Argon` and `CodexC-GPT5.6Terra` have not reviewed yet. At 213751f, backend `./gradlew check` passes with no warnings, web `npm run lint && npm run typecheck && npm test && npm run build` passes (22 tests), and `npm run gen:api` leaves no diff. No change touched the API spec.

## Done
- ClaudeC-Opus5.5 full review of the whole scope against the checklist in "Goal and acceptance". Fixes, each with a test that fails without it: F1 cacba92, F2 4f5b2f4, F3 14c4f05, F5 f768ba5. Tests for untested paths: F4 3411e2f, F6 213751f. `backend/intent.md` notes the 72-byte password limit (4f5b2f4).
- Checked by ClaudeC-Opus5.5 and found sound (no finding):
  - Per-user isolation tests cover list, save, delete and lookup `saved`.
  - Login gives the same 401 and detail for an unknown email and a wrong password, and `DaoAuthenticationProvider` hashes a dummy password for unknown users, so timing doesn't reveal accounts. Register's 409 does reveal them, which the contract requires.
  - Cookie attributes are as recorded, and CORS is off unless `CORS_ORIGINS` is set.
  - Env vars are read only through `@ConfigurationProperties`, and `.env.example` lists them all.
  - The migration matches the entities (Hibernate `validate` runs in every integration test).
  - The spec matches the controllers (drift test), and the generated client matches the spec.
  - `DictionaryProvider` returns our own shapes with ids from `SenseIds`, so a Yandex provider plugs in without callers changing.

## Tried and rejected
- F3 via `INSERT ... ON CONFLICT DO NOTHING` (ClaudeC-Opus5.5): rejected, because it needs a native insert of every copied column (arrays, jsonb) alongside the entity mapping. A retry in a fresh transaction reuses the existing code path.
- F3 test as a thread race (ClaudeC-Opus5.5): rejected, because it could pass with broken code. The test instead holds an uncommitted conflicting insert in a second connection and waits for the save to block on it (`pg_stat_activity`).
- F2 by switching to a password encoder without BCrypt's limit (ClaudeC-Opus5.5): not done, because it changes the stored hash format, which is a decision rather than a defect fix.
- Reopening the Step 1 handoff for this: rejected, archived handoffs are read-only (`AGENTS.md`), and a cross-check has its own goal and acceptance.

## Decisions made during this task
- All three implementers review and fix, then Fable reviews (by Alex, 2026-11-06): "I want that all three reviewed the code, fix findings and only then Fable reviews it again".
- A per-agent coverage table and status rules tied to it (ClaudeC-Fable, 2026-11-06): the driver starts the review when all implementers in a row report nothing to do, so without a record of who has reviewed, an implementer could pass its turn without reviewing.

## Open questions
- Next agent: local setup on this machine. There is no Docker and no `java` on `PATH`, and `JAVA_HOME` is not set. A portable JDK 25 and PostgreSQL 16 live in `AgentsOutput/ClaudeC-Opus5.5/`: set `JAVA_HOME` in your own shell to `<repo>/AgentsOutput/ClaudeC-Opus5.5/jdk/jdk-25.0.5+8` before calling `./gradlew`. PostgreSQL was accepting connections on `localhost:5432` when this file was written, with the same users and databases as `docker-compose.yml`; if it is down, start it from that folder with `pg/pgsql/bin/pg_ctl -D pgdata -l pg.log start`. `backend/.env` exists. Use that folder, don't edit files in it (it belongs to another agent), and don't write its paths into repo files other than this handoff.
- Next agent: tests and local runs use the fake dictionary provider only; never call the live Yandex API (`AGENTS.md`).

## Next step
1. `AgyC-Gemini4Argon`, `CodexC-GPT5.6Terra`, each in your first round (`ClaudeC-Opus5.5` has done its review): do your full review of the scope in "Goal and acceptance" (rule 1), record findings, fix defects with tests (rule 2), run the backend and web checks, fill in your row in "Review coverage", update this handoff and commit.
2. In any later round: follow rule 6 and the status rules above.
3. Reviewer (`ClaudeC-Fable`), when the implementers have stopped: check the acceptance list. If a row in "Review coverage" is empty, set `in-progress` and say which agent still owes its review. Decide every `proposal` and every recorded disagreement, review the fix commits, re-run all checks. Copy accepted proposals that change a decision into the matching `intent.md` before closing.

## Session log
- 2026-11-06T12:24:36Z ClaudeC-Fable: Opened this handoff at Alex's request after confirming from history and run logs that Gemini and Codex never worked on Step 1. No code changed.
- 2026-11-06T12:52:00Z ClaudeC-Opus5.5: Round 1 full review. Recorded F1–F10, fixed F1–F6 in six commits (cacba92..213751f), all checks pass. Status stays in-progress because code changed.

## Outcome
<!-- Filled in only when archiving. -->
