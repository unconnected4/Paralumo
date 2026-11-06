---
task: step1-crosscheck
title: Cross-check of the Step 1 backend by all three implementers, then review
status: ready-for-review
branch: main
started: 2026-11-06T12:24:36Z
last_updated: 2026-11-06T15:50:00Z
last_commit: 091e31b
agents: [ClaudeC-Fable, ClaudeC-Opus5.5, AgyC-Gemini4Argon, CodexC-GPT5.6Terra]
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
| AgyC-Gemini4Argon | 2026-11-06, round 2 | F7–F9, F11–F18 | 2b3fabf |
| CodexC-GPT5.6Terra | 2026-11-06, round 3 | F7–F9, F19 | 4821f73 |

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
- F7 [proposal] Session revocation: logout only clears the cookie. The JWT stays valid for 30 days, so a copied cookie or a bearer token (the planned Android transport) can't be signed out, and a password change couldn't end other sessions. This follows the recorded stateless-JWT decision, so I left the code alone. Proposal: before bearer clients ship (or with account settings), add revocation, e.g. a `token_version` column on `users` checked by `SessionTokenFilter` (one primary-key read per authenticated request), or server-side sessions. Found by: ClaudeC-Opus5.5, AgyC-Gemini4Argon, CodexC-GPT5.6Terra. Status: proposal.
- F8 [proposal] Logout CSRF: `POST /api/auth/logout` takes no body and needs no cookie, so a cross-site form can sign a user out. The recorded CSRF decision accepts "no body" endpoints, and the only effect is a nuisance logout. Proposal: accept as is and say so in the CSRF note in `backend/intent.md`; requiring a JSON body would change the contract for no real gain. Found by: ClaudeC-Opus5.5, AgyC-Gemini4Argon, CodexC-GPT5.6Terra. Status: proposal.
- F9 [proposal] Brute force: login and register have no rate limiting or lockout. This isn't in Step 1's plan, and the right mechanism (proxy or app-level) depends on hosting, which is TBD. Proposal: add it to `docs/plan.md` as a pre-production item tied to the hosting decision. Found by: ClaudeC-Opus5.5, AgyC-Gemini4Argon, CodexC-GPT5.6Terra. Status: proposal.
- F10 [defect] A validly signed token for a user id with no `users` row (local DB reset with the same `AUTH_SECRET`) makes `POST /api/cards` hit the `user_cards.user_id` foreign key: a 500 (a problem body since F5). Found by: ClaudeC-Opus5.5. Status: rejected (nothing deletes users in production; a stale local cookie is cleared by signing in again, since `/api/auth/me` already answers 401 for it; account deletion, when it comes, needs revocation anyway, see F7. ClaudeC-Opus5.5).
- F11 [defect] `service/UserService.authenticate`: contrary to F2's note, Spring Security 7's `BCrypt.checkpw` silently truncates candidate passwords at 72 UTF-8 bytes, so an account with a 72-byte password accepted any wrong password sharing the first 72 bytes with 200 OK, and `authenticate` hashed arbitrarily large strings. `authenticate` now rejects passwords over 72 chars or 72 UTF-8 bytes as `Optional.empty()` (401, no contract change) before `AuthenticationManager` runs. Found by: AgyC-Gemini4Argon. Status: fixed 2a74bd0.
- F12 [defect] `api/Dtos.RegisterRequest`: `UserService.normalizeEmail` trimmed outer spaces on login, whereas `RegisterRequest` ran `@Email` on the untrimmed input, rejecting padded emails such as `"  learner@example.com  "` with 422 instead of normalizing and registering. `RegisterRequest`'s compact constructor now trims `email` before Bean Validation runs. Found by: AgyC-Gemini4Argon. Status: fixed 269fe19.
- F13 [defect] `api/AuthApiTest`: `backend/AGENTS.md` requires a per-user isolation test on every endpoint that touches user data; `GET /api/auth/me` was never tested with more than one user in `users`. Added `meReturnsOnlyTheAuthenticatedUser` covering cookie and bearer auth across two users. Found by: AgyC-Gemini4Argon. Status: fixed 0c9ebcb.
- F14 [defect] `config/CorsProperties`: `SecurityConfig` sets `allowCredentials(true)`, so setting `CORS_ORIGINS=*` passed startup and threw `IllegalArgumentException` (500) at request time, violating `docs/engineering.md`'s fail-fast configuration rule; origin entries were also untrimmed, and CORS had no tests. `CorsProperties` now trims origins and rejects `*` at startup, with unit and integration tests for allowed/disallowed origins and the default CORS-off path. Found by: AgyC-Gemini4Argon. Status: fixed 0edf694.
- F15 [defect] `config/SessionTokenFilter`: `resolveToken` checked `header.startsWith("Bearer ")` case-sensitively, ignoring lowercase `"bearer <token>"` or `"BEARER <token>"` headers despite RFC 9110 §11.1 making HTTP auth scheme names case-insensitive. Now matches the prefix with `regionMatches(true, ...)` and trims surrounding whitespace while keeping invalid or blank bearer headers non-null so they never fall back to a stale cookie. Found by: AgyC-Gemini4Argon. Status: fixed cd15cf5.
- F16 [defect] `web/src/mocks/handlers.ts`, `web/src/api/client.test.ts`: MSW handlers had drifted from `docs/api/openapi.json` and the backend — `register` did not enforce email format, `minLength: 8`, or max 72 UTF-8 bytes (masking a contract bug in `client.test.ts` where the duplicate-email 409 test sent 5-char `'other'`, a 422 on the real backend), emails were neither trimmed nor lowercased on register/login, `login` accepted >72-byte passwords, whitespace-only strings bypassed `!body?.field`, malformed JSON threw 500 instead of 422, `POST /api/cards` read an undocumented `notes` request field, and error responses used `application/json` instead of `application/problem+json`. Aligned handlers and client tests with the contract. Found by: AgyC-Gemini4Argon. Status: fixed 7eed89e.
- F17 [improvement] `api/CardApiTest`: `backend/intent.md` requires saved cards to retain copied sense data when `senses`/`lexemes` later change and soft-deleted cards to keep `created_at`, `notes`, `custom_example`, and copied fields when restored; neither invariant was directly tested, and `listsNewestFirst` relied on wall-clock advancement between back-to-back saves. Added a test covering both invariants and spaced `listsNewestFirst` timestamps explicitly. Found by: AgyC-Gemini4Argon. Status: fixed 3b16280.
- F18 [improvement] `api/AuthApiTest`, `api/HealthControllerTest`, `config/SessionTokensTest`: three paths lacked test coverage — `UserService.register`'s concurrent duplicate-email `DataIntegrityViolationException` -> 409 catch block, `HealthController.ready()`'s 503 problem-detail branch when `JdbcTemplate` throws `DataAccessException`, and `SessionTokens`'s `Path=/api` and `clearingCookie()` attributes. Added deterministic tests for all three. Found by: AgyC-Gemini4Argon. Status: fixed 4ec1fb5.
- F19 [defect] `web/src/mocks/handlers.ts`: direct `GET /api/dictionary/lookup?q=` returned a 422 problem response, while the backend and OpenAPI contract define a 200 `{query: '', lexemes: []}` response. `client.lookup` short-circuits blank input, which hid the permanent mock drift from its existing test. Found by: CodexC-GPT5.6Terra. Status: fixed 4821f73.
- F20 [defect] `web/src/mocks/handlers.ts`: F16's problem bodies used three `detail` strings the backend never sends: `Word 'x' not found` (backend: `Word "x" not found`), `Sense '<id>' not found` (backend: `Sense not found`) and `Request body is missing or malformed JSON` (backend: `... or not valid JSON`). `SearchPage` shows `detail` to the user, so mock mode showed wording the real app doesn't. Aligned, with a test that fails on the old strings. Found by: ClaudeC-Opus5.5. Status: fixed 6ecc7e9.

## Current state
Update (ClaudeC-Opus5.5, round 4 of the second driver run, 20261106T152737Z): read every commit since 6ecc7e9 (4d9aaf1, 74a8f46, 091e31b); all three touch only this handoff, so there is nothing in scope to check. Backend `./gradlew check --rerun-tasks` passes with no warnings; web `npm run lint && npm run typecheck && npm test && npm run build` passes (25 tests) and `npm run gen:api` leaves no diff. No code changed, so status is `ready-for-review` (third of the three-implementer streak): the implementers have stopped and `ClaudeC-Fable` should run Next step item 3. The paragraphs below are superseded by this update.

Update (CodexC-GPT5.6Terra, round 3 of the second driver run, 20261106T152737Z): reviewed every in-scope commit since dc8945e, specifically F20's 6ecc7e9 mock-detail alignment. Its three changed strings exactly match `LookupService`, `CardService`, and `ApiExceptionHandler`; its regression test exercises all of them. Nothing remains to fix. Backend `./gradlew check` passes with no warnings, and web `npm run lint && npm run typecheck && npm test && npm run build` passes (25 tests); `npm run gen:api` leaves no diff. No code changed this round, so status remains `ready-for-review` (second of the three-implementer streak). The paragraphs below are superseded by this update.

Update (AgyC-Gemini4Argon, round 2 of the second driver run, 20261106T152737Z): read every commit since 52f9dba (4821f73..4d9aaf1; 937847d belongs to `agent-orchestration`). Both F19 (4821f73) and F20 (6ecc7e9) are sound and match the backend and `docs/api/openapi.json`; no defects remain. Backend `./gradlew check --rerun-tasks --no-build-cache` passes with no warnings, web `npm run lint && npm run typecheck && npm test && npm run build` passes (25 tests, no warnings), and `npm run gen:api` leaves no diff. No code changed in this round, so status is `ready-for-review` (first of the three-implementer streak). The paragraphs below are superseded by this update.

Update (ClaudeC-Opus5.5, round 1 of the second driver run, 20261106T152737Z): read every commit since 2b3fabf (2a74bd0..937847d; 937847d belongs to `agent-orchestration` and touches nothing in scope). The backend changes for F11–F15, F17, F18 and the web changes for F16, F19 are sound, except F20 (mock detail wording), fixed in 6ecc7e9. Backend `./gradlew check` passes with no warnings; web lint, typecheck, test (25 tests) and build pass, and `npm run gen:api` leaves no diff. The paragraphs below are superseded by this update.

Update (round 3): all three implementers have now completed their full reviews. CodexC-GPT5.6Terra independently confirmed F7–F9 and found/fixed F19 (4821f73). Backend `./gradlew check` passes with no warnings; web `npm run lint && npm run typecheck && npm test && npm run build` passes (24 tests), and `npm run gen:api` leaves no diff. The following paragraph is superseded by this update.

`ClaudeC-Opus5.5` (round 1, F1–F10) and `AgyC-Gemini4Argon` (round 2, F7–F9 confirmed, F11–F18 recorded and fixed in 2a74bd0..4ec1fb5) have completed their full reviews. `CodexC-GPT5.6Terra` has not reviewed yet, and `ClaudeC-Opus5.5` has not yet inspected the round 2 commits. At 4ec1fb5, backend `./gradlew check --rerun-tasks --no-build-cache` passes with zero warnings, web `npm run lint && npm run typecheck && npm test && npm run build` passes (23 tests), and `npm run gen:api` leaves no diff. No change touched `docs/api/openapi.json`.

## Done
- ClaudeC-Opus5.5, second driver run round 4 (rule 6): reviewed 6ecc7e9..091e31b, which changed only this handoff. Nothing to fix; all backend and web checks pass cleanly and API generation leaves no diff.
- CodexC-GPT5.6Terra, second driver run round 3 (rule 6): reviewed dc8945e..74a8f46, including the only in-scope code change, F20 (6ecc7e9). Confirmed every mock `detail` corrected there exactly matches the relevant backend source and the regression test covers the three cases. Found nothing to fix; backend and web checks passed cleanly, and API generation left no diff.
- AgyC-Gemini4Argon, second driver run round 2 (rule 6): reviewed 4821f73..4d9aaf1. Verified F19 (4821f73: direct empty or whitespace `GET /api/dictionary/lookup` returns 200 `{query: '', lexemes: []}`, matching `DictionaryController` and `LookupService`) and F20 (6ecc7e9: every `problem(...)` `detail` string in `web/src/mocks/handlers.ts` now matches the exact wording emitted by `LookupService`, `CardService`, `AuthController`, `SecurityConfig`, `Dtos` and `ApiExceptionHandler`). Found nothing left to fix; re-ran all backend and web checks cleanly.
- ClaudeC-Opus5.5, second driver run round 1 (rule 6): reviewed 2a74bd0..4821f73. Fixed F20 in 6ecc7e9. Checked and found sound: F11's early return skips the dummy hash, but for known and unknown emails alike, so it reveals no accounts; F12's trim runs in the record's canonical constructor, which Jackson uses; F14's startup exception fails binding as the fail-fast rule wants; F15 still never falls back to the cookie on a blank or forged bearer header; the F18 registration race test uses the same blocked-connection pattern as F3's.
- CodexC-GPT5.6Terra independent full review of the whole scope at 4821f73. Confirmed F7–F9 proposals and fixed F19's empty-query contract drift in the permanent MSW handler, with a direct-handler test.
- ClaudeC-Opus5.5 full review of the whole scope against the checklist in "Goal and acceptance". Fixes, each with a test that fails without it: F1 cacba92, F2 4f5b2f4, F3 14c4f05, F5 f768ba5. Tests for untested paths: F4 3411e2f, F6 213751f. `backend/intent.md` notes the 72-byte password limit (4f5b2f4).
- AgyC-Gemini4Argon independent full review of the whole scope at 2b3fabf, including `cacba92..2b3fabf`. Defects fixed with tests: F11 2a74bd0, F12 269fe19, F13 0c9ebcb, F14 0edf694, F15 cd15cf5, F16 7eed89e. Test-coverage and determinism improvements: F17 3b16280, F18 4ec1fb5. Independently confirmed F7–F9 proposals and verified F1, F3–F6 fixes and F10 rejection rationale.
- Checked by ClaudeC-Opus5.5 and AgyC-Gemini4Argon and found sound (no finding):
  - Per-user isolation tests now cover every user-data endpoint (`GET /api/auth/me`, `GET /api/cards`, `POST /api/cards`, `DELETE /api/cards/{id}`, and `GET /api/dictionary/lookup` `saved` flags).
  - Login gives the same 401 and detail for an unknown email, a wrong password, and an over-72-byte password, and `DaoAuthenticationProvider` hashes a dummy password for unknown users, so timing doesn't reveal accounts. Register's 409 does reveal them, which the contract requires.
  - Cookie and clearing-cookie attributes (`HttpOnly`, `SameSite=Lax`, `Path=/api`, `Secure` in production) are tested, and CORS fails fast on `*` and stays off unless `CORS_ORIGINS` is set.
  - Env vars are read only through `@ConfigurationProperties`, and `.env.example` lists them all.
  - The migration matches the entities (Hibernate `validate` runs in every integration test), and unique-constraint races are deterministically tested on lookup, card save, and user registration.
  - The spec matches the controllers (drift test), the generated client matches the spec, and MSW handlers match the spec.
  - `DictionaryProvider` returns our own shapes with ids from `SenseIds`, so a Yandex provider plugs in without callers changing.

## Tried and rejected
- F3 via `INSERT ... ON CONFLICT DO NOTHING` (ClaudeC-Opus5.5): rejected, because it needs a native insert of every copied column (arrays, jsonb) alongside the entity mapping. A retry in a fresh transaction reuses the existing code path.
- F3 test as a thread race (ClaudeC-Opus5.5): rejected, because it could pass with broken code. The test instead holds an uncommitted conflicting insert in a second connection and waits for the save to block on it (`pg_stat_activity`).
- F2 by switching to a password encoder without BCrypt's limit (ClaudeC-Opus5.5): not done, because it changes the stored hash format, which is a decision rather than a defect fix.
- Reopening the Step 1 handoff for this: rejected, archived handoffs are read-only (`AGENTS.md`), and a cross-check has its own goal and acceptance.
- F11 via `@MaxUtf8Bytes(72)` on `LoginRequest` (AgyC-Gemini4Argon): rejected, because a wrong >72-byte password at sign-in should return 401 `Invalid email or password` like any other wrong password without changing `docs/api/openapi.json` to make overlong login passwords a 422. Checking length and UTF-8 byte count in `UserService.authenticate` stops `BCrypt.checkpw` truncation and short-circuits oversized inputs in O(1) before UTF-8 encoding or hashing.

## Decisions made during this task
- All three implementers review and fix, then Fable reviews (by Alex, 2026-11-06): "I want that all three reviewed the code, fix findings and only then Fable reviews it again".
- A per-agent coverage table and status rules tied to it (ClaudeC-Fable, 2026-11-06): the driver starts the review when all implementers in a row report nothing to do, so without a record of who has reviewed, an implementer could pass its turn without reviewing.

## Open questions
- Next agent: local setup on this machine. There is no Docker and no `java` on `PATH`, and `JAVA_HOME` is not set. A portable JDK 25 and PostgreSQL 16 live in `AgentsOutput/ClaudeC-Opus5.5/`: set `JAVA_HOME` in your own shell to `<repo>/AgentsOutput/ClaudeC-Opus5.5/jdk/jdk-25.0.5+8` before calling `./gradlew`. PostgreSQL was accepting connections on `localhost:5432` when this file was written, with the same users and databases as `docker-compose.yml`; if it is down, start it from that folder with `pg/pgsql/bin/pg_ctl -D pgdata -l pg.log start`. `backend/.env` exists. Use that folder, don't edit files in it (it belongs to another agent), and don't write its paths into repo files other than this handoff.
- Next agent: tests and local runs use the fake dictionary provider only; never call the live Yandex API (`AGENTS.md`).

## Next step
00. Supersedes items 0–2 below: all three implementers have reported `ready-for-review` in a row (Gemini, Codex, Opus) with no code change since 6ecc7e9. `ClaudeC-Fable` should run item 3.

0. Supersedes item 1 below where they differ: `AgyC-Gemini4Argon` and `CodexC-GPT5.6Terra` have inspected commits through 74a8f46 and reported `ready-for-review`. `ClaudeC-Opus5.5` should inspect commits since 4d9aaf1 per rule 6 and set `ready-for-review` if nothing remains, completing the three-implementer streak so `ClaudeC-Fable` runs item 3.

1. Supersedes the following stale first-round instruction: ClaudeC-Opus5.5 and AgyC-Gemini4Argon should inspect commits since their respective previous rounds (including 4821f73), following rule 6. Record or fix any defect in those changes; otherwise set `ready-for-review`. CodexC-GPT5.6Terra should inspect only commits since 4821f73 in a later round.

1. `CodexC-GPT5.6Terra`, in your first round (`ClaudeC-Opus5.5` and `AgyC-Gemini4Argon` have done their full reviews): do your full review of the scope in "Goal and acceptance" (rule 1), record findings, fix defects with tests (rule 2), run the backend and web checks, fill in your row in "Review coverage", update this handoff and commit.
2. `ClaudeC-Opus5.5` and `AgyC-Gemini4Argon`, in any later round: follow rule 6 (inspect commits since your last round) and the status rules above.
3. Reviewer (`ClaudeC-Fable`), when the implementers have stopped: check the acceptance list. If a row in "Review coverage" is empty, set `in-progress` and say which agent still owes its review. Decide every `proposal` and every recorded disagreement, review the fix commits, re-run all checks. Copy accepted proposals that change a decision into the matching `intent.md` before closing.

## Session log
- 2026-11-06T15:46:00Z CodexC-GPT5.6Terra: Second driver run (20261106T152737Z) round 3, rule 6 review of dc8945e..74a8f46. Verified F20's 6ecc7e9 mock-detail fix against the backend sources and test; all backend and web checks pass with no generated API diff. Nothing left to do; status remains ready-for-review.
- 2026-11-06T15:35:00Z ClaudeC-Opus5.5: Second driver run (20261106T152737Z) round 1, rule 6 review of 2a74bd0..937847d. Recorded and fixed F20 (6ecc7e9); all backend and web checks pass. Status stays in-progress because code changed.
- 2026-11-06T13:54:07Z CodexC-GPT5.6Terra: Round 3 independent full review at 4821f73. Confirmed F7–F9, recorded and fixed F19; backend `check` and all web checks including API generation pass. Status stays in-progress because code changed in this round.
- 2026-11-06T12:24:36Z ClaudeC-Fable: Opened this handoff at Alex's request after confirming from history and run logs that Gemini and Codex never worked on Step 1. No code changed.
- 2026-11-06T12:52:00Z ClaudeC-Opus5.5: Round 1 full review. Recorded F1–F10, fixed F1–F6 in six commits (cacba92..213751f), all checks pass. Status stays in-progress because code changed.
- 2026-11-06T13:45:00Z AgyC-Gemini4Argon: Round 2 independent full review at 2b3fabf. Confirmed F7–F9, recorded and fixed F11–F18 in eight commits (2a74bd0..4ec1fb5), all backend and web checks pass. Status stays in-progress because code changed.
- 2026-11-06T15:39:30Z AgyC-Gemini4Argon: Second driver run (20261106T152737Z) round 2, rule 6 review of 4821f73..4d9aaf1. Verified F19 and F20 fixes and re-ran all backend and web checks with zero warnings; nothing left to do for this task, status set to ready-for-review.
- 2026-11-06T15:50:00Z ClaudeC-Opus5.5: Second driver run (20261106T152737Z) round 4, rule 6 review of 6ecc7e9..091e31b (handoff-only commits). All backend and web checks pass with no warnings and no generated API diff. Nothing left to do; status ready-for-review.

## Outcome
<!-- Filled in only when archiving. -->
