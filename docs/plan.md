# Paralumo — plan for steps 0–2 (web UI + backend + translator-dictionary), step 3 outline

## Context
`intent.md` sets out the build order: a shared multi-user backend with word/sense/spaced-repetition (SRS) storage, then an English→Russian translator-dictionary with a per-sense "+" button to save senses into the user's vocabulary, then a "Match the words" game that runs on real vocabulary. The web app comes first; Android later; iOS only if people want it.

Decisions (see also `../intent.md` → Cross-cutting decisions, and each project's intent.md):
- **Language pair:** English → Russian only (the learner studies English and sees Russian glosses)
- **Dictionary source:** Yandex Dictionary API (`dict.1.1/json/lookup`, `lang=en-ru`)
- **Backend:** Java (Spring Boot + Spring Data JPA + Flyway + Postgres, built with Gradle), by Alex on 2026-10-26, replacing the earlier FastAPI plan (see `../backend/intent.md`)
- **Frontend:** React + Vite + TypeScript SPA; Android gets a separate native app later
- The backend is a plain JSON API so the future Android app can use it unchanged

## Step 0 — Web UI implementation (first, by Alex)
- **Implement Web UI first, backend follows (by Alex):** "we are implementing web ui now. Based on them we will implement backend." Do not scaffold anything in `backend/` yet.
- **Scaffold `web/`:** React + Vite + TypeScript SPA with Tailwind CSS.
- **API module & Types (P16, P20):** `web/src/api/` defines the TypeScript models (`Lexeme`, `Sense`, `Card`, `User`, etc.) and a hand-written fetch client (`lookup`, `saveCard`, `deleteCard`, `listCards`, `login`, `register`, `logout`, `getMe`). Production code imports only from `src/api/`, never from `src/mocks/`. In Step 1, this module is replaced by the generated client (`npm run gen:api`).
- **Mock layer in `web/` (P16, P17, P20, P22):** MSW handlers and fixtures are authored directly in `web/src/mocks/` (e.g. for "run", "bank", "light", plus not-found and error cases). MSW intercepts network requests at `/api/...`, maintains in-memory state for saved cards and the signed-in user, and returns 401 when unauthenticated. Mock mode is triggered by `VITE_API_MOCKS=1` (`npm run dev:mock`) and tree-shaken out of production builds. `docs/api/examples/` is dropped to avoid duplication.
- **Build Core UI Surfaces:**
  - Search view: search input, phonetic transcription, client-side Web Speech audio playback.
  - Senses grouped by Part of Speech (noun, verb, etc.).
  - Sense card showing Russian gloss, synonyms, English meanings, example sentences, and interactive `+` → `✓` toggle.
  - "My Words" view: list/deck of saved cards with local/mock state.
  - Simple login/register screens and session indicator in header driven by `src/api/` auth calls (P20).
- **Backend implementation follows:** Once the Web UI is built and verified, the proven UI data structures dictate the backend schemas, endpoints, and OpenAPI contract.

## Repo layout
Monorepo with independently delivered projects. The full map is in the root [AGENTS.md](../AGENTS.md).
```
backend/             Spring Boot app (scaffolded in Step 1 based on Web UI)
web/                 Vite + React + TS (with src/mocks/ for MSW)
e2e/                 Playwright tests across web + backend
docs/                shared docs; docs/api/openapi.json is the API contract
docker-compose.yml   Postgres for local dev
```
## Build strategy: UI-first
Start outside-in by building and validating the Web UI (`web/`) with its own mock layer before touching `backend/`. The UI defines the user experience, required fields, and interaction flows. The backend is then built to satisfy that proven contract.

## Step 1 — Backend implementation & data model (based on Web UI)
Scaffold `backend/` with Gradle, Spring Boot, DTO records, JPA entities and Flyway migrations matching the Web UI contract. Export `docs/api/openapi.json`, and transition `web/` to the generated client (`npm run gen:api`).
- **Handover from Step 0 (P24):** The Step 0 types and client functions in `web/src/api/` are the input for the backend's DTOs and controllers. Step 1 is done when the generated client replaces the interim module and `npm run typecheck` passes with pages, hooks and MSW handlers unchanged apart from imports.
- **Auth contract from Step 0 differs from Spring Security defaults.** The web UI calls JSON `POST /api/auth/login` and `/api/auth/register` with `{email, password}`, `POST /api/auth/logout` and `GET /api/auth/me`; it expects `401` for an unknown email or wrong password, `409` for an existing email, and `{detail}` error bodies. Spring Security's form login takes form data (`username`, `password`) at `/login` and answers with redirects, it has no registration endpoint, and its errors aren't `{detail}` bodies. Because of the P24 handover condition, Step 1 either exposes the Step 0 shape (our own JSON controllers over Spring Security's `AuthenticationManager`) or changes `web/src/api/` and the MSW handlers deliberately, in the same commit, with the reason recorded.
- `users`: id, email, password_hash, created_at. Auth uses Spring Security with one JWT carried in an httpOnly cookie (for web) or a bearer header (for Android).
- `lexemes`: id, lemma, lang='en', pos, transcription. Unique on (lemma, pos).
- `senses`: id, lexeme_id, source, source_key (matches the public `sense_id` string from Step 0), translation_ru, synonyms_ru[], meanings_en[], examples jsonb [{en, ru}].
- `user_cards`: id, user_id, sense_id, **a copy of the sense data**, optional `notes` (text) and `custom_example` (text), `created_at`, `deleted_at` (soft delete to preserve review logs), plus SRS state (due_at, stability/interval, difficulty/ease, reps, lapses, state). Unique on `(user_id, sense_id)`. Re-saving a deleted card restores it (`deleted_at = NULL`), keeping its existing copied sense data, notes, custom examples, and FSRS state intact. Active card queries filter `deleted_at IS NULL` centrally in `service/CardService`.
- `review_logs`: card_id, reviewed_at, rating (1–4), source ('srs' | 'match_game'), previous and new state. Preserved even if a card is deleted so the algorithm can change or train without losing history.
- `lookup_cache`: query and response json with a timestamp / TTL, used as a deduplicating cache. Quota throttling/rate limiting is handled as a separate mechanism if needed.
- SRS algorithm: start with FSRS, wrapped in `service/SrsService` (library choice open, see `../backend/intent.md`).

## Step 2 — Translator-dictionary
Backend:
- `provider/yandex/`: calls the lookup endpoint and turns the response (`def[] → tr[] → syn/mean/ex`) into our `Lexeme`/`Sense` structures.
- `GET /api/dictionary/lookup?q=`: returns the lexemes and senses, each marked `saved: bool` for the current user.
- `POST /api/cards {sense_id}`, `DELETE /api/cards/{id}`, `GET /api/cards` (the user's vocabulary list).

Web:
- Login/register pages, a search box, results grouped by part of speech, and one sense card each (Russian translation, synonyms, English meanings, examples) with a "+" button that shows ✓ once saved. Plus a "My words" list page.
- Audio pronunciation: Web Speech API (`speechSynthesis`) is primary audio, feature-detected (`'speechSynthesis' in window` and voice check) to hide when unsupported.
- Show the Yandex credit line if the terms require it.

## Step 3 — Match game (outline, planned in detail later)
- `GET /api/game/match?n=6` picks due or weak cards first, then fills from random cards.
- Match results are logged with `source='match_game'` and only nudge SRS state lightly (e.g. rating=3 on first-attempt match, rating=1 on mistake), because matching is a recognition task and pairs can be solved by elimination.

## Verification
Testing tiers, CI and local setup are defined in [engineering.md](engineering.md).

- Backend: JUnit tests covering the fake provider and stub routes (Step 0), the Yandex→model mapping against recorded provider responses (once Yandex is integrated), the card save/unsave endpoints and their uniqueness, FSRS scheduling, and per-user isolation.
- End to end: automated in `e2e/` (Playwright, fake dictionary provider). Register, search "run", add two senses, and check they appear in "My words" and in `user_cards`.
