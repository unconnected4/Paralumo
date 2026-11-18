---
task: feedback-amendments
title: Apply Alex's feedback F1–F3 - agree the contract first, then implement it
status: in-progress
phase: contract                   # contract | implementation; switched only by the reviewer (see Goal)
branch: main
started: 2026-11-18T11:38:37Z
last_updated: 2026-11-18T11:38:37Z
last_commit: 9251e2e
agents: [Claude-Opus5.5-Interactive]
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
- The docs named in proposal section 4 are updated.
- Backend `./gradlew check` and web lint, typecheck, tests and build pass with no warnings. If `e2e/` has tests by then, they pass too.

## Current state
Phase 1 (`contract`) has not started. The draft proposal was written by Claude-Opus5.5-Interactive from Alex's answers. It contains open proposals that agents should test hard:
- the sense id change and the reset of local data (1)
- the cache max-age values (2)
- the 100-id limit (2)
- signed-out buttons (3.6)
- language names in UI text (3.7)

## Contract agreement
<!-- Phase 1 only. One line per implementer per cycle: "- <agent>, cycle <n>: agree (<remarks>)". A proposal change after a line makes earlier lines stale; agreement then starts over. -->

## Done
- Draft contract written: `docs/api/proposals/feedback-amendments.md`.

## Tried and rejected
- Filtering `GET /api/cards` by `sense_id` for saved state. Rejected by Alex, who prefers two separate endpoints.
- Firing the lookup and the saved-state request together, keyed by the word (`?lemma=`). Rejected because a card's stored lemma may not match the provider's normalisation of the query, and Alex chose "lookup first, then saved state".

## Decisions made during this task
- Two phases in one task, switched by the reviewer (by Alex, see Goal). It's one task rather than two so the implementation starts automatically once the reviewer approves, using the driver's existing reviewer `in-progress` route. The driver's note to that cycle will say "the reviewer recorded findings". At the switch, read Next step and `phase` instead.
- During phase 1, agreement is recorded only in this handoff, not in the proposal. The driver counts handoff-only rounds as "nothing to do", so a full streak of agreements leads to review.

## Open questions
- None yet.

## Next step
Phase 1, `contract`. Review [docs/api/proposals/feedback-amendments.md](../../api/proposals/feedback-amendments.md) as described in Goal → Phase 1. Either improve it (`in-progress`) or record your agreement under "Contract agreement" (`ready-for-review`).

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

## Outcome
<!-- Filled in only when archiving. -->
