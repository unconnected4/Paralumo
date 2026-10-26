---
task: step1-backend
title: Step 1 — Backend implementation and data model, based on the Step 0 web UI
status: in-progress
branch: main
started: 2026-09-26T21:00:46Z
last_updated: 2026-10-26T16:18:51Z
last_commit: 02f0e94
agents: [ClaudeC-Fable]
related_decisions: [intent.md, docs/plan.md, backend/intent.md, web/intent.md, docs/handoff/archive/handoff-20260926T195210Z.md]
---

# Step 1 — Backend implementation and data model, based on the Step 0 web UI

## Goal and acceptance
Implement the backend the Step 0 web UI proved it needs (`docs/plan.md` → Step 1): scaffold `backend/` as a Java project (Gradle, Spring Boot, Spring Data JPA, Flyway, Postgres); implement the endpoints the interim client in `web/src/api/client.ts` calls; export `docs/api/openapi.json`; replace the interim client with the generated one.

Acceptance (P24, `docs/plan.md`): `npm run gen:api` replaces the interim module and `npm run typecheck` passes with pages, hooks and MSW handlers unchanged apart from imports. Backend `./gradlew check` (format, Error Prone + NullAway, unit and integration tests) passes with no warnings.

## Current state
Not started. This file was opened by the Step 0 review (ClaudeC-Fable, 2026-09-26) to hold the review's inputs for Step 1. The Step 0 web UI is the contract: read `web/src/api/types.ts`, `web/src/api/client.ts` and `web/src/mocks/handlers.ts` first; the handlers document every status code the UI relies on.

Contract as the web UI uses it today:
- `GET /api/dictionary/lookup?q=` → `{query, lexemes[]}`, senses carry `saved: bool` for the current user (false when signed out). Unknown word → `404 {detail}`. Empty query → `{query: '', lexemes: []}` (the client short-circuits this without a request).
- `GET /api/cards` → `Card[]` (401 when signed out). `POST /api/cards {sense_id}` → `201 Card`, or `200` with the existing card if that sense is already saved; `404` unknown sense; `422` missing `sense_id`. `DELETE /api/cards/{card_id}` → `204`, `404` unknown card.
- `POST /api/auth/login` and `/register` with JSON `{email, password}` → `User` (`401` bad credentials, `409` duplicate email, `400` missing fields). `POST /api/auth/logout` → `204`. `GET /api/auth/me` → `User` or `401`.
- Error bodies are `{detail: string}`.

## Done
- Nothing yet.

## Tried and rejected
- Nothing yet.

## Decisions made during this task
- Backend is Java, not FastAPI (by Alex, 2026-10-26): "backend is going to be implemented in Java." Alex picked Spring Boot, Gradle with the Kotlin DSL, and Spring Data JPA + Flyway. Stack, layout and commands are in `backend/AGENTS.md` and `backend/intent.md`.
- Step 0 decisions that bind Step 1: public `sense_id` is an opaque deterministic string (`backend/intent.md`); auth contract shape differs from Spring Security defaults (`docs/plan.md` Step 1); the sample words "run", "bank", "light" must exist in the fake provider because the web UI offers them as shortcuts (`web/intent.md`).

## Open questions
- For Alex: should the ✓ "Saved" badge on the search page toggle back to unsave? Today it is static; unsaving is only possible from "My words". The lookup response carries `saved: bool` but not the card id, so unsaving from search needs either a `card_id` on saved senses or a delete-by-sense endpoint. This changes the Step 1 contract, so it is cheaper to decide before the backend exists. Not blocking: Step 1 can implement the current contract and add either later.
- For Alex: cards have a `notes` field (shown in "My words", seeded in the mock data), but the UI has no way to create or edit notes. Should Step 1 include notes editing (`PATCH /api/cards/{id}`), or should `notes` be dropped from the contract until it is designed? Not blocking.
- Next agent: keep `404` for an unknown word, or return `200` with an empty `lexemes` list? The UI handles both today (a dedicated "Word not found" state for 404). 404 is the Step 0 contract; change it only with a reason recorded here.
- Next agent: `web/intent.md` says the audio button feature-detects "and voice check", but `AudioButton.tsx` only checks `'speechSynthesis' in window` (voices load asynchronously in Chrome, so a synchronous voice check would hide the button wrongly). Either implement a proper asynchronous check or drop the words from the intent. Not a Step 1 blocker.

## Next step
Scaffold `backend/` following `backend/AGENTS.md` and `docs/plan.md` Step 1: Gradle wrapper, version catalog on current majors, Spotless, Error Prone + NullAway and `-Werror` first, so the no-warnings rule holds from the first commit. Then the fake dictionary provider containing at least "run", "bank" and "light" in the shape of `web/src/mocks/fixtures.ts`, then the routes above, then the OpenAPI export and `npm run gen:api`.

## Session log
- 2026-09-26T21:00:46Z ClaudeC-Fable: Reviewed Step 0 (commits 3dd5343..67eb91b); fixed URL-driven search, the mock-mode flag, ApiError, labelled auth inputs, return-after-sign-in, strict MSW in tests; opened this handoff with the contract summary and open questions. Commit: see `git log --grep="Agent: ClaudeC-Fable"`.
- 2026-10-26T16:18:51Z ClaudeC-Fable: Switched the backend plan from FastAPI to Java (Spring Boot, Gradle, JPA + Flyway) at Alex's request; rewrote backend/AGENTS.md and backend/intent.md and updated shared docs. No code yet.

## Outcome
<!-- Filled in only when archiving. -->
