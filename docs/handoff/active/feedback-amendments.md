---
task: feedback-amendments
title: Apply Alex's feedback F1–F3 - agree the contract first, then implement it
status: in-progress
phase: contract                   # contract | implementation; switched only by the reviewer (see Goal)
branch: main
started: 2026-11-18T11:38:37Z
last_updated: 2026-11-18T12:31:05Z
last_commit: 4812af1
agents: [Claude-Opus5.5-Interactive, ClaudeC-Opus5.5, AgyC-Gemini4Argon, CodexC-GPT5.6Terra]
related_decisions: [intent.md, docs/api/proposals/feedback-amendments.md, backend/intent.md, web/intent.md, docs/plan.md]
---

# Apply Alex's feedback F1–F3: agree the contract first, then implement it

## Goal and acceptance
Alex's feedback (collected in task `alex-feedback`, now archived) changes the API contract, the database schema and the web client:
- **F1:** a language-neutral model.
- **F2:** language annotations.
- **F3:** a user-independent, cacheable lookup, plus a separate saved-state endpoint.

The draft contract is [docs/api/proposals/feedback-amendments.md](../../api/proposals/feedback-amendments.md). Points there tagged `by Alex` are settled. Everything else is a proposal.

**How the task runs (by Alex):** "I want at first all the agents agree on the contracts, when it is done and Fable confirms it is ok we switch to the implementation step of the amendments." The work runs in two phases. The front matter field `phase` says which phase is active, and only the reviewer changes it.

### Phase 1: `contract`
Agents agree the contract. Nothing else changes.
- **Only these files may change:** the proposal file and this handoff file. Don't touch `backend/`, `web/`, `e2e/` or `docs/api/openapi.json`, and don't change other docs.
- **Each implementer, in turn:**
  1. Read the proposal against the current code and contract: `docs/api/openapi.json`, `backend/src`, `web/src/api`, `web/src/mocks` and the pages.
  2. Check it is complete: every renamed field, every consumer, the migration, the MSW handlers and the tests' expectations.
  3. Check it is consistent with F1–F3, and that it can be implemented within `backend/AGENTS.md`, `web/AGENTS.md` and `docs/engineering.md`.
- **If something should change:** edit the proposal and say why in its text or in Decisions below, then set `status: in-progress`. A changed proposal restarts the agreement round, because the driver counts it as work.
- **If you agree with the proposal as it stands:** add one line under "Contract agreement" below (`- <agent>, cycle <n>: agree`, with any remarks), and set `status: ready-for-review`. Record agreement only in this handoff, never in the proposal file: an edit to the proposal counts as work and would reset the streak.
- **Never reopen a point tagged `by Alex`.** If a point can't be implemented as Alex stated it, add a `For Alex:` question and set `status: blocked`.
- **Phase 1 is complete when:** every implementer has agreed in a row, with no proposal change in between, and the reviewer confirms.

**Reviewer at the end of phase 1:**
1. Check the proposal is complete, internally consistent, consistent with F1–F3, and implementable.
2. If you have findings, put them at the top of Next step, keep `phase: contract`, and set `status: in-progress`.
3. If it's OK:
   - In the proposal, change its Status line to `**agreed**` with the date and your agent name.
   - Set `phase: implementation` in the front matter.
   - Replace Next step with the phase 2 list below.
   - Set `status: in-progress`.
4. Never set `done` in phase 1, because the acceptance criteria below include the implementation.

### Phase 2: `implementation`
Implement the agreed proposal exactly.
- **If implementing shows the contract must change:** update the proposal first, with the reason in Decisions, then change the code. Don't change the code silently.
- **Normal implementer and reviewer rules apply** (`scripts/orchestrate/prompts/`). The reviewer sets `done` only when every item below holds.

### Acceptance (whole task)
- The proposal's Status is `agreed`, and the code matches it.
- No language names in identifiers, types, columns, JSON fields or file names in `backend/src`, `web/src` or `docs/api/openapi.json`. User-facing text that names languages is allowed (proposal 3.7).
- The language annotations are present and fixed to `en`/`ru`.
- The lookup is user-independent, with cache headers checked by a test. `GET /api/cards/saved` exists, with its 401/422 cases and a per-user isolation test.
- The web follows proposal section 3, with tests for:
  - buttons inactive until the saved state arrives
  - no saved-state request when signed out
  - the retry state
- `docs/api/openapi.json` has been re-exported and `web/src/api/schema.d.ts` regenerated.
- The docs named in proposal section 5 are updated.
- Backend `./gradlew check` and web lint, typecheck, tests and build pass with no warnings. If `e2e/` has tests by then, they pass too.

## Current state
Phase 1 (`contract`), cycle 1. CodexC-GPT5.6Terra found and resolved one implementation-critical cache-header ambiguity while checking the full proposal against the current API, backend and web consumers. The proposal changed, so the agreement streak restarts. Proposals still open to challenge by the implementers:
- the cache max-age values (2)
- the 100-id limit (2)
- signed-out buttons (3.6)
- language names in UI text (3.7)
- the revisions made in cycle 1 (Decisions)

## Contract agreement
<!-- Phase 1 only. One line per implementer per cycle: "- <agent>, cycle <n>: agree (<remarks>)". A proposal change after a line makes earlier lines stale; agreement then starts over. -->
- AgyC-Gemini4Argon, cycle 1: agree (checked against `backend/src`, `web/src`, `V1__create_users_lexemes_senses_cards.sql` and `docs/api/openapi.json`; all cycle 1 revisions and open proposals — cache TTLs, 100-id cap, signed-out active "+" buttons, and keeping language names in UI copy — are complete and consistent with F1–F3)

## Done
- Draft contract written: `docs/api/proposals/feedback-amendments.md`.
- Cycle 1, ClaudeC-Opus5.5: draft checked against `backend/src`, `web/src` and the security and error-handling config, then revised (Decisions). A new section 4 lists every consumer to update, so the old section 4 is now section 5.
- Cycle 1, AgyC-Gemini4Argon: verified the revised contract against `backend/src`, `web/src` and `docs/api/openapi.json`, and recorded agreement.
- Cycle 1, CodexC-GPT5.6Terra: verified the proposal against the current controllers, shared exception handler, security configuration, provider/persistence model, generated client, hooks, views and MSW handlers. Clarified the lookup-local cache-header path for both successful and 404 responses.

## Tried and rejected
- Filtering `GET /api/cards` by `sense_id` for saved state. Rejected by Alex, who prefers two separate endpoints.
- Firing the lookup and the saved-state request together, keyed by the word (`?lemma=`). Rejected because a card's stored lemma may not match the provider's normalisation of the query, and Alex chose "lookup first, then saved state".
- Recomputing the sense ids in SQL inside V2 to keep local cards (ClaudeC-Opus5.5). Rejected: Postgres `lower()` follows the collation and can differ from Java's `toLowerCase(Locale.ROOT)`, so ids could silently mismatch. Telling developers to recreate the database was also rejected, because a skipped doc step leaves stale rows.
- `ExampleSentence.source` as the new name for `en` (draft). Replaced by `text`, because `source` already names the provider (`senses.source`, `DictionaryProvider.source()`).
- Backend `./gradlew.bat check` in this session. Blocked before compilation because this machine has no JDK 25 toolchain; Docker is also unavailable on `PATH`, so its required integration-test infrastructure cannot be started. This is environment setup, not a contract defect.
- Web `npm run lint`, `npm run typecheck`, `npm test -- --run`, and `npm run build`. All commands exited 0; the production build completed without warnings.

## Decisions made during this task
- Two phases in one task, switched by the reviewer (by Alex, see Goal). It's one task rather than two so the implementation starts automatically once the reviewer approves, using the driver's existing reviewer `in-progress` route. The driver's note to that cycle will say "the reviewer recorded findings". At the switch, read Next step and `phase` instead.
- During phase 1, agreement is recorded only in this handoff, not in the proposal. The driver counts handoff-only rounds as "nothing to do", so a full streak of agreements leads to review.
- Proposal revisions, cycle 1 (ClaudeC-Opus5.5). Each one is a proposal that later agents may challenge:
  - Sense id includes both languages: confirmed. The V2 migration deletes `user_cards`, `senses` and `lexemes` rows and keeps `users`, because there is no production data. As a result, the JSONB example keys need no rewrite.
  - The provider is the single source of the language pair (`DictionaryProvider.pair()` returning a `LanguagePair` record). This replaces the draft's "configuration or a constant".
  - `ExampleSentence.text` instead of `.source`.
  - Caching details: only the lookup's 404 is cacheable, not the shared `NotFoundException`; other errors stay `no-store`; `Vary: Origin` is allowed; a token on the request never changes the response. The lookup keeps its `SenseCatalog` write on cache misses.
  - Saved-state endpoint details: repeated-parameter form; a missing parameter gives 422, not Spring's default 400, through an `ApiExceptionHandler` override; 401 wins over 422; duplicate ids allowed; schema `SavedSense`, operation id `listSavedSenses`.
  - Web: the lookup is never invalidated by save, unsave or auth changes; the saved-state key includes the user id; sign-out removes saved-state queries; a delete on My Words updates the saved-state cache too.
  - Cache-header implementation, cycle 1 (CodexC-GPT5.6Terra): `NotFoundException` currently reaches the shared `ApiExceptionHandler`, which must not make every card 404 publicly cacheable. The proposal now requires `DictionaryController` to handle its own lookup 404 and to return successful lookups via `ResponseEntity`, making the specified 200 and lookup-only 404 headers implementable without changing the shared handler.

## Open questions
- None yet.

## Next step
Phase 1, `contract`. Review [docs/api/proposals/feedback-amendments.md](../../api/proposals/feedback-amendments.md) as described in Goal → Phase 1. The cycle 1 cache-header clarification changed the proposal, so agreement must restart. Either improve it (`in-progress`) or record your agreement under "Contract agreement" (`ready-for-review`).

<!-- Phase 2 list, for the reviewer to paste into Next step when switching phase:
1. Backend: V2 migration, then provider records, entities, DTOs, fake data, the sense id (as agreed), the lookup without user state and with cache headers, and `GET /api/cards/saved`. Tests updated and added. Run `./gradlew check`.
2. Re-export `docs/api/openapi.json` and run `npm run gen:api`.
3. Web: neutral names, the split requests, the inactive buttons, retry state and signed-out behaviour, the MSW handlers and fixtures, and tests. Run web lint, typecheck, tests and build.
4. Docs from proposal section 4.
5. e2e, if tests exist.
-->

## Session log
<!-- Append one line per session. Never rewrite earlier lines. -->
- 2026-11-18T11:38:37Z Claude-Opus5.5-Interactive: Composed F1–F3 into this task and the draft contract. Phase 1 is ready to run.
- 2026-11-18T12:30:00Z ClaudeC-Opus5.5: Cycle 1. Checked the proposal against the code, settled the sense-id/reset question, filled gaps in caching, saved-state validation, the web cache and the consumer list. Proposal changed, so status is in-progress.
- 2026-11-18T12:24:00Z AgyC-Gemini4Argon: Cycle 1. Verified the revised contract proposal against backend/src, web/src, V1 migration and docs/api/openapi.json. Found nothing left to change in the proposal; recorded agreement under Contract agreement (status ready-for-review).
- 2026-11-18T12:31:05Z CodexC-GPT5.6Terra: Cycle 1. Checked the proposal against the current controllers, exception handling, persistence/provider model and web consumers. Clarified the controller-local cache response path; proposal changed, so status is in-progress and agreement restarts. Web checks passed; backend checks are blocked by the missing JDK 25 and Docker.

## Outcome
<!-- Filled in only when archiving. -->
