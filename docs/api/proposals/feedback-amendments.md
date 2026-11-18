# Contract amendments: language-neutral model, lookup split from saved state

Status: **draft, under agreement** (phase 1 of task `feedback-amendments`, handoff `docs/handoff/active/feedback-amendments.md`).

This is the contract that the implementation phase builds. Agents edit this file until they agree on it. The reviewer then marks it agreed, and from that point it changes only with a reason recorded in the handoff. Once implemented, `docs/api/openapi.json` (generated) is the contract, and this file stays as the record of why it changed.

Source: Alex's feedback F1–F3, recorded in [docs/handoff/archive/handoff-20261118T113926Z.md](../../handoff/archive/handoff-20261118T113926Z.md) (task `alex-feedback`) and summarised in the root `intent.md`. Points tagged `by Alex` below are settled. The rest is the drafting agent's proposal and open to change.

## Settled by Alex
- **F1 (by Alex):** "Lexems are linked to Russian in code, it is definitely incorrect. Russian can be hardcoded as target language for now, but it should not be in the code structure." This covers the API contract and the database schema too (by Alex), so `translation_ru` becomes `translation`.
- **F2 (by Alex):** "source language and translation must have languages annotations. For now, they can be hardcode to English for source and to Russian for translation."
- **F3 (by Alex):** translation and cards get separate endpoints. "get translation first (this request can be cached on reverse proxy), when we got an answer fire a request for saved cards if applicable." Saved state has its own endpoint, not a filter on `GET /api/cards` ("I prefer to have two different endpoints"). Until the saved state arrives, the +/✓ buttons are in "some sort of inactive state".

## 1. Language-neutral model (F1, F2)
Language codes are ISO 639-1 lowercase strings (`en`, `ru`). The pair is fixed at `en` → `ru` for now: the backend has a single configured pair, and nothing in names, types, columns or file names mentions a language.

Where the annotations live:
- `source_lang` on the lexeme. The lemma, transcription, meanings and example source text are in this language.
- `target_lang` on the sense. The translation, synonyms and example translations are in this language.
- A card is self-contained (it copies the sense), so it carries both `source_lang` and `target_lang`.

Renames (API JSON field → new name; Java and TypeScript follow the same names in camelCase):

| Today | New | Language |
|---|---|---|
| `Sense.translation_ru` | `Sense.translation` | target |
| `Sense.synonyms_ru` | `Sense.synonyms` | target |
| `Sense.meanings_en` | `Sense.meanings` | source |
| `ExampleSentence.en` | `ExampleSentence.source` | source |
| `ExampleSentence.ru` | `ExampleSentence.translation` | target |
| `Card.translation_ru`, `Card.synonyms_ru`, `Card.meanings_en` | `Card.translation`, `Card.synonyms`, `Card.meanings` | as above |
| — | `Lexeme.source_lang` (required) | |
| — | `Sense.target_lang` (required) | |
| — | `Card.source_lang`, `Card.target_lang` (required) | |

Request side: `GET /api/dictionary/lookup` takes no language parameters while the pair is fixed. Adding `source`/`target` query parameters later is a backward-compatible change.

Backend structure: provider records (`ProviderSense`, `ProviderLexeme`, `ExampleSentence`), entities, DTOs, services and the fake data JSON (`src/main/resources/fake-data/*.json`) use the neutral names. The fixed pair comes from one place (configuration, or a single constant read only by the provider layer), never from a literal scattered through the code.

Database (a new Flyway migration `V2__language_neutral_columns.sql`; V1 is never edited):
- `lexemes.lang` → `source_lang`. The unique key becomes `(source_lang, lemma, pos)`.
- `senses`: `translation_ru` → `translation`, `synonyms_ru` → `synonyms`, `meanings_en` → `meanings`. Add `target_lang TEXT NOT NULL`, backfilled with `'ru'`.
- `user_cards`: the same renames on the copied sense columns. Add `source_lang` and `target_lang`, backfilled with `'en'` / `'ru'`.
- Example sentences in the JSONB columns change keys from `{en, ru}` to `{source, translation}`, rewritten by the migration.

Sense id: today it is a hash of provider + lemma + pos + main translation. Proposal: also include `source_lang` and `target_lang`, so the same lemma in two pairs never collides. This changes every existing `sense_id`. There is no production data, so existing local cards are reset (the migration may delete local `user_cards`, `senses` and `lexemes` rows, or the docs tell developers to recreate their local database). Agents: confirm or reject this, with the reason.

## 2. Lookup split from saved state (F3)
### `GET /api/dictionary/lookup?q=`
- Doesn't depend on the user. The response has no `saved` and no `card_id`. It is the same signed in or out, and the endpoint never reads the session (permitted for everyone, no user lookup).
- Response: `{query, source_lang, target_lang, lexemes[]}`, with lexemes and senses as in section 1. (The root-level pair is a convenience for clients; the per-lexeme and per-sense annotations are authoritative.)
- Errors are unchanged: unknown word → `404 {detail}`; empty query → `200` with no lexemes.
- Caching, so a reverse proxy can cache it:
  - `200`: `Cache-Control: public, max-age=3600`.
  - `404`: `Cache-Control: public, max-age=300`.
  - No `Set-Cookie`, and no `Vary: Cookie` or `Vary: Authorization`.
  - Spring Security adds `Cache-Control: no-cache, no-store` to every response by default, so this endpoint must override it, and a test must check the headers.
  - The max-age values are a proposal. The Yandex terms allow only temporary caching (root `intent.md`, Open questions), so they must stay within whatever the terms permit once that is known.

### `GET /api/cards/saved?sense_id=<id>&sense_id=<id>...` (new)
- Signed-in only: `401 {detail}` when signed out.
- Returns which of the given senses the current user has saved: `200 [{sense_id, card_id}]`, one entry per saved sense, only active (not deleted) cards, and only the current user's. Senses that aren't saved are left out. An unknown sense id isn't an error: it's simply not saved.
- At least 1 and at most 100 `sense_id` values. No value or more than 100 → `422 {detail}`. A lookup returns far fewer senses than 100.
- Never cached: `Cache-Control: private, no-store`.
- Per-user isolation test: another user's cards never appear.

### Unchanged endpoints apart from the renames
- `GET /api/cards`, `POST /api/cards {sense_id}`, `DELETE /api/cards/{card_id}`.
- Auth endpoints and health endpoints.

## 3. Web behaviour (F3)
1. The search page requests the lookup and renders it as soon as it arrives.
2. If the user is signed in, and only once the lookup has answered with at least one sense, the page requests `GET /api/cards/saved` with that lookup's sense ids.
3. Until that answer arrives, every +/✓ button is inactive: disabled, in a neutral style that is neither "+" nor ✓, with an accessible label such as "Checking saved state". Then each button becomes active as "+" or ✓.
4. If the saved-state request fails, the buttons stay inactive and the page shows a short inline retry message. The translation stays visible.
5. Saving or unsaving updates the saved-state cache for that sense directly, so the buttons don't refetch.
6. Signed-out users never request saved state. Their buttons are active "+" immediately, and clicking one leads to sign-in, as today (`web/src/pages/SearchPage.tsx`). This is a proposal: Alex said the buttons are inactive until the saved state arrives, and signed-out users have no saved state to wait for.
7. Card views (`MyWordsPage`, `SenseCard`) use the neutral field names. User-facing text that names the languages (e.g. the empty state in `SearchPage.tsx`, "Search for English words to see Russian translations") is presentation, not code structure. Proposal: keep it as text for now, and don't derive it from the language codes until there is a second pair.
8. The MSW mock layer (`web/src/mocks/`) follows the same split: a user-independent lookup handler, a separate saved-state handler with the same 401/422 rules, and fixtures with the neutral names and language fields.

## 4. What changes outside the code
- `docs/api/openapi.json`: re-exported, never hand-edited. `web/src/api/schema.d.ts` is regenerated (`npm run gen:api`).
- `docs/plan.md` (data model, Step 2 endpoint list), `backend/intent.md`, `web/intent.md` and `docs/engineering.md` (where they name fields or describe the lookup) are updated to match.
- The e2e test, if one exists by then (task `e2e-smoke`), still passes.
