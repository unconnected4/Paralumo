---
task: step1-backend
title: Step 1 — Backend implementation and data model, based on the Step 0 web UI
status: in-progress
branch: main
started: 2026-09-26T21:00:46Z
last_updated: 2026-11-06T10:26:58Z
last_commit: dada4ea
agents: [ClaudeC-Fable, ClaudeC-Opus5.5]
related_decisions: [intent.md, docs/plan.md, backend/intent.md, web/intent.md, docs/handoff/archive/handoff-20260926T195210Z.md]
---

# Step 1 — Backend implementation and data model, based on the Step 0 web UI

## Goal and acceptance
Implement the backend the Step 0 web UI proved it needs (`docs/plan.md` → Step 1): scaffold `backend/` as a Java project (Gradle, Spring Boot, Spring Data JPA, Flyway, Postgres); implement the endpoints the interim client in `web/src/api/client.ts` calls; export `docs/api/openapi.json`; replace the interim client with the generated one.

Acceptance (P24, `docs/plan.md`): `npm run gen:api` replaces the interim module and `npm run typecheck` passes with pages, hooks and MSW handlers unchanged apart from imports. Backend `./gradlew check` (format, Error Prone + NullAway, unit and integration tests) passes with no warnings.

## Current state
Implemented, not yet reviewed. `backend/` is scaffolded and serves the whole Step 0 contract below; `docs/api/openapi.json` is exported from it; `web/src/api/` now uses the client generated from that spec. Backend `./gradlew check` and web lint, typecheck, tests and build all pass (verified 2026-11-06). Step 1 acceptance (P24) holds: `npm run gen:api` reproduces the committed `schema.d.ts`, and pages, hooks and MSW handlers are unchanged. Not done yet: review, e2e against the real backend, CI workflows.

Contract as the web UI uses it today:
- `GET /api/dictionary/lookup?q=` → `{query, lexemes[]}`, senses carry `saved: bool` for the current user (false when signed out). Unknown word → `404 {detail}`. Empty query → `{query: '', lexemes: []}` (the client short-circuits this without a request).
- `GET /api/cards` → `Card[]` (401 when signed out). `POST /api/cards {sense_id}` → `201 Card`, or `200` with the existing card if that sense is already saved; `404` unknown sense; `422` missing `sense_id`. `DELETE /api/cards/{card_id}` → `204`, `404` unknown card.
- `POST /api/auth/login` and `/register` with JSON `{email, password}` → `User` (`401` bad credentials, `409` duplicate email, `400` missing fields). `POST /api/auth/logout` → `204`. `GET /api/auth/me` → `User` or `401`.
- Error bodies are `{detail: string}`.

## Done
- Backend scaffold (ClaudeC-Opus5.5, 2026-10-28): Gradle 9.8.2 wrapper, version catalog, Spring Boot with JPA, Flyway, Security and springdoc; Spotless (google-java-format), Error Prone + NullAway, `-Werror` and `org.gradle.warning.mode=fail`.
- Fake dictionary provider with `run`, `bank` and `light` (`src/main/resources/fake-data/`), deterministic `sense_id`s (`provider/SenseIds`).
- Endpoints: dictionary lookup with per-user `saved`; cards list, save (201, or 200 when already saved; soft-deleted cards are restored), delete; auth register, login, logout, me, with a JWT in an httpOnly cookie or a bearer header; health. Errors are `{detail}` problem bodies.
- Flyway `V1`: `users`, `lexemes`, `senses`, `user_cards` (with `notes`, `custom_example`, `deleted_at`).
- Tests: unit tests (sense ids, tokens, fake provider) and integration tests against Postgres, including per-user isolation for cards and the OpenAPI drift check (`OpenApiContractTest`; `./gradlew exportOpenApi` writes the spec).
- Web: `gen:api` runs openapi-typescript into `src/api/schema.d.ts`; `types.ts` names the generated schemas; `client.ts` keeps its functions and `ApiError` on top of `openapi-fetch`.
- Recovery session (ClaudeC-Opus5.5, 2026-11-06): the 2026-10-28 session was interrupted after the code and a passing `check`, but before the handoff update and commit. This session reviewed the uncommitted tree against the contract and `docs/plan.md` Step 1, found nothing missing, re-ran every check without build caches, and committed it unchanged apart from this file.

## Tried and rejected
- google-java-format 1.37.0: Spotless 8.10.4 calls an API it removed (`NoSuchMethodError`), so it stays pinned at 1.36.1 (see `backend/intent.md`).
- Typing springdoc schemas through `spring.jackson.property-naming-strategy` alone: springdoc uses swagger-core's own Jackson 2 mapper, which ignores it, so `config/OpenApiConfig` sets SNAKE_CASE there too.

## Decisions made during this task
- Backend is Java, not FastAPI (by Alex, 2026-10-26): "backend is going to be implemented in Java." Alex picked Spring Boot, Gradle with the Kotlin DSL, and Spring Data JPA + Flyway. Stack, layout and commands are in `backend/AGENTS.md` and `backend/intent.md`.
- Step 0 decisions that bind Step 1: public `sense_id` is an opaque deterministic string (`backend/intent.md`); auth contract shape differs from Spring Security defaults (`docs/plan.md` Step 1); the sample words "run", "bank", "light" must exist in the fake provider because the web UI offers them as shortcuts (`web/intent.md`).

- Step 1 implementation choices (ClaudeC-Opus5.5, 2026-10-28) are recorded in `backend/intent.md` → "Step 1 implementation choices" and `web/intent.md` → "Generated client" and "TypeScript 6 peer override". In short: hashed `s_…` sense ids; lookups record what they return so saving can resolve a sense; `lookup_cache`, `review_logs` and SRS columns wait for the steps that use them; 422 for every validation error (the MSW handlers still say 400 for auth, which the UI doesn't distinguish); 30-day JWT in a `paralumo_session` cookie, SameSite=Lax, no CSRF tokens; `VITE_API_BASE_URL` is now an origin.
- Unknown word stays `404 {detail}` (Step 0 contract), covered by `DictionaryApiTest.unknownWordIs404WithDetail`.

## Open questions
- For Alex: should the ✓ "Saved" badge on the search page toggle back to unsave? Today it is static; unsaving is only possible from "My words". The lookup response carries `saved: bool` but not the card id, so unsaving from search needs either a `card_id` on saved senses or a delete-by-sense endpoint. This changes the Step 1 contract, so it is cheaper to decide before the backend exists. Not blocking: Step 1 can implement the current contract and add either later.
- For Alex: cards have a `notes` field (shown in "My words", seeded in the mock data), but the UI has no way to create or edit notes. Should Step 1 include notes editing (`PATCH /api/cards/{id}`), or should `notes` be dropped from the contract until it is designed? Not blocking.
- Next agent: `web/intent.md` says the audio button feature-detects "and voice check", but `AudioButton.tsx` only checks `'speechSynthesis' in window` (voices load asynchronously in Chrome, so a synchronous voice check would hide the button wrongly). Either implement a proper asynchronous check or drop the words from the intent. Not a Step 1 blocker.
- Next agent: `npm audit` in `web/` reports 16 advisories (4 high: vite, undici, zod, source-map-js; moderate: vitest, eslint 10 and typescript-eslint through minimatch). All are in dev tooling that predates Step 1, none in the packages Step 1 added. `npm audit fix` covers the vite/vitest/undici/zod ones; the eslint ones offer only a downgrade to eslint 9, which the current-majors rule rules out, so wait for upstream. A separate dependency-update change, not Step 1.
- Next agent: this machine has no Docker. The 2026-10-28 and 2026-11-06 sessions used a portable JDK 25 and PostgreSQL 16 in `AgentsOutput/ClaudeC-Opus5.5/` (set `JAVA_HOME` to its `jdk/jdk-25.0.5+8`; start the database with `pg/pgsql/bin/pg_ctl -D pgdata -l pg.log start`), with the same users and databases as `docker-compose.yml`. Other agents may read it, but must not rely on it: it isn't in the repo.

## Next step
Review Step 1 (reviewer role, commits from `dada4ea` on). After that: run the web UI in backend mode (`npm run dev` against `./gradlew bootRun`) and add the e2e smoke test in `e2e/`, then the CI workflows (`.github/workflows/backend.yml` and `web.yml`, which `docs/engineering.md` describes but which don't exist yet). Archive this handoff when those are done or split into their own tasks.

## Session log
- 2026-09-26T21:00:46Z ClaudeC-Fable: Reviewed Step 0 (commits 3dd5343..67eb91b); fixed URL-driven search, the mock-mode flag, ApiError, labelled auth inputs, return-after-sign-in, strict MSW in tests; opened this handoff with the contract summary and open questions. Commit: see `git log --grep="Agent: ClaudeC-Fable"`.
- 2026-10-26T16:18:51Z ClaudeC-Fable: Switched the backend plan from FastAPI to Java (Spring Boot, Gradle, JPA + Flyway) at Alex's request; rewrote backend/AGENTS.md and backend/intent.md and updated shared docs. No code yet.
- 2026-10-28 ClaudeC-Opus5.5: Scaffolded and implemented the backend, exported the spec and moved web to the generated client; `check` passed. Interrupted before the handoff update and commit.
- 2026-11-06T10:26:58Z ClaudeC-Opus5.5: Reviewed the leftover uncommitted work, found it complete, re-ran backend `check` (no caches) and web lint/typecheck/tests/build (all pass), updated this handoff and committed it all.

## Outcome
<!-- Filled in only when archiving. -->
