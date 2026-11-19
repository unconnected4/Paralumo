# Contract amendments: language-neutral model, lookup split from saved state

Status: **agreed** (2026-11-18, ClaudeC-Fable, at the end of phase 1 of task `feedback-amendments`, handoff `docs/handoff/active/feedback-amendments.md`).

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
| `ExampleSentence.en` | `ExampleSentence.text` | source |
| `ExampleSentence.ru` | `ExampleSentence.translation` | target |
| `Card.translation_ru`, `Card.synonyms_ru`, `Card.meanings_en` | `Card.translation`, `Card.synonyms`, `Card.meanings` | as above |
| — | `Lexeme.source_lang` (required) | |
| — | `Sense.target_lang` (required) | |
| — | `Card.source_lang`, `Card.target_lang` (required) | |

`ExampleSentence` uses `text`, not `source`, because `source` already means the provider in the backend (`senses.source`, `DictionaryProvider.source()`, the first input of the sense id). Using one word for two things invites mix-ups.

Request side: `GET /api/dictionary/lookup` takes no language parameters while the pair is fixed. Adding `source`/`target` query parameters later is a backward-compatible change.

Backend structure: provider records (`ProviderSense`, `ProviderLexeme`, `ExampleSentence`), entities, DTOs, services and the fake data JSON (`src/main/resources/fake-data/*.json`) use the neutral names. The fixed pair comes from one place, never from a literal scattered through the code: the provider reports it. `DictionaryProvider` gets a `LanguagePair pair()` method (a record `LanguagePair(String source, String target)`). The fake provider returns `en`/`ru` from one constant, and the Yandex provider will derive it from its `lang` parameter. `SenseCatalog`'s `LANG_EN` constant goes, and the catalog, the sense id and the DTO mapping all read the pair from the provider.

Database (a new Flyway migration `V2__language_neutral_columns.sql`; V1 is never edited):
- `lexemes.lang` → `source_lang`, without the `DEFAULT 'en'` (the service always sets it). The unique key becomes `(source_lang, lemma, pos)`, and the lexeme lookup in `SenseCatalog` (`findByLemmaAndPos`) includes `source_lang`.
- `senses`: `translation_ru` → `translation`, `synonyms_ru` → `synonyms`, `meanings_en` → `meanings`. Add `target_lang TEXT NOT NULL`, backfilled with `'ru'`.
- `user_cards`: the same renames on the copied sense columns. Add `source_lang` and `target_lang`, backfilled with `'en'` / `'ru'`.
- Example sentences in the JSONB columns change keys from `{en, ru}` to `{text, translation}`. The migration empties these tables (below), so there is nothing to rewrite, and the persistence record `Example` simply uses the new names.

Sense id: today it is a hash of provider + lemma + pos + main translation. It will also include `source_lang` and `target_lang` (`SenseIds.of(source, pair, lemma, pos, translation)`), so the same lemma and translation in two pairs never collide. That is a real risk on the target side, because related languages share spellings (`банк` is both Russian and Ukrainian, for example). This changes every existing `sense_id`.

The V2 migration itself resets existing local data. Before the renames, it deletes every row from `user_cards`, `senses` and `lexemes`, in that order because of the foreign keys. `users` is kept. Reasons:
- There is no production data: hosting is still TBD (root `intent.md`).
- Old rows can't be kept as they are. Their `source_key`/`sense_key` would never match a new lookup, so saved words would show as "+". Saving one again would create a second `senses` row and a second card for the same meaning.
- Recomputing the hashes in SQL was rejected. Postgres `lower()` depends on the collation and can differ from Java's `toLowerCase(Locale.ROOT)`, so a recomputed id could silently differ from the one the backend computes.
- Putting the reset in the migration, rather than telling developers to recreate their database, means nobody ends up with stale rows by skipping a doc step.
- This makes V2 a one-off exception to the `backend/AGENTS.md` rule that a migration is backward-compatible with the previous release (expand → migrate → contract): it renames columns in place and deletes rows. Nothing is deployed yet, so there is no previous release to keep running, and the sense-id change would make compatibility moot anyway. Record the exception in `backend/intent.md` (section 5); from V3 on, the rule applies again (reviewer clarification, ClaudeC-Fable, 2026-11-18).

## 2. Lookup split from saved state (F3)
### `GET /api/dictionary/lookup?q=`
- Doesn't depend on the user. The response has no `saved` and no `card_id`. It is the same signed in or out, and the endpoint never reads the session (permitted for everyone, no user lookup).
- Response: `{query, source_lang, target_lang, lexemes[]}`, with lexemes and senses as in section 1. (The root-level pair is a convenience for clients; the per-lexeme and per-sense annotations are authoritative.)
- Errors are unchanged: unknown word → `404 {detail}`; empty query → `200` with no lexemes.
- Caching, so a reverse proxy can cache it:
  - `200`: `Cache-Control: public, max-age=3600`.
  - `404`: `Cache-Control: public, max-age=300`. Only the lookup's 404 is cacheable. `NotFoundException` is shared with the card endpoints (unknown sense on save, unknown card on delete), so `DictionaryController` handles the lookup's exception locally and returns its normal problem body with this header; the shared exception handler remains unchanged. The controller returns the successful lookup through `ResponseEntity` with the 200 header too, including for an empty query.
  - Any other status (`5xx`, or a `4xx` other than 404) keeps the default `no-store`, so an outage is never cached.
  - An empty query's `200` gets the same header as any other `200`.
  - No `Set-Cookie`, and no `Vary: Cookie` or `Vary: Authorization`. `Vary: Origin`, and the other `Vary` values Spring's CORS support adds, are allowed and expected: they keep the CORS headers correct in a shared cache. The header test checks that `Vary` doesn't name `Cookie` or `Authorization`. It doesn't require `Vary` to be absent.
  - A request with a session cookie or bearer token gets exactly the same response as one without, whether the token is valid, expired or forged. The browser still sends the cookie with the lookup. `public` is what lets a shared cache store a response to a request with `Authorization` (RFC 9111, section 3.5).
  - The lookup keeps one side effect. On every request that reaches the backend (a cache miss), it records the returned lexemes and senses in `lexemes`/`senses` (`SenseCatalog`), because `POST /api/cards` resolves a sense id through that table. This doesn't depend on the user, so it doesn't conflict with caching: a cached response only carries ids that an earlier, uncached request recorded. The exception is a database reset behind a warm proxy, which needs the proxy cache cleared as well.
  - Spring Security adds `Cache-Control: no-cache, no-store` to every response by default, so this endpoint must override it, and a test must check the headers.
  - The max-age values are a proposal. The Yandex terms allow only temporary caching (root `intent.md`, Open questions), so they must stay within whatever the terms permit once that is known.

### `GET /api/cards/saved?sense_id=<id>&sense_id=<id>...` (new)
- Signed-in only: `401 {detail}` when signed out.
- Returns which of the given senses the current user has saved: `200 [{sense_id, card_id}]`, one entry per saved sense, only active (not deleted) cards, and only the current user's. Senses that aren't saved are left out. An unknown sense id isn't an error: it's simply not saved.
- At least 1 and at most 100 `sense_id` values, sent as a repeated query parameter (OpenAPI `style: form, explode: true`, which the generated web client sends by default). A lookup returns far fewer senses than 100.
- `422 {detail}` when there is no `sense_id`, more than 100, or a blank one. By default Spring answers all three with `400`, so `ApiExceptionHandler` overrides both sources to `422`, as it already does for invalid bodies:
  - a missing required parameter raises `MissingServletRequestParameterException`;
  - the limit and the blank check, written as constraints on the parameter (`@Size(min = 1, max = 100) List<@NotBlank String>`), go through Spring's built-in method validation and raise `HandlerMethodValidationException`. That built-in validation runs on its own when a parameter carries a constraint; don't put `@Validated` on the controller class. Spring skips the built-in path for a class annotated `@Validated` and uses the AOP interceptor instead, which throws `ConstraintViolationException`, and that would be a 500 (verified in `HandlerMethod.checkArguments`, spring-web 7.0.10; reviewer clarification, ClaudeC-Fable, 2026-11-18).
  When the user is signed out, `401` wins over `422`, because security runs before the controller.
- Duplicate ids are allowed and count towards the limit as sent. The response has at most one entry per sense, and the order of its entries has no meaning.
- Schema name `SavedSense` (`{sense_id, card_id}`, both required). Operation id `listSavedSenses`.
- Never cached. Spring Security's default header (`Cache-Control: no-cache, no-store, max-age=0, must-revalidate`), which every other card endpoint already carries, does this; the controller sets nothing (reviewer clarification, ClaudeC-Fable, 2026-11-18: the draft said `private, no-store`, which would have meant a bespoke header on this one endpoint for no gain).
- Per-user isolation test: another user's cards never appear.

### Unchanged endpoints apart from the renames
- `GET /api/cards`, `POST /api/cards {sense_id}`, `DELETE /api/cards/{card_id}`.
- Auth endpoints and health endpoints.

## 3. Web behaviour (F3)
1. The search page requests the lookup and renders it as soon as it arrives.
2. If the user is signed in, and only once the lookup has answered with at least one sense, the page requests `GET /api/cards/saved` with that lookup's sense ids.
3. Until that answer arrives, every +/✓ button is inactive: disabled, in a neutral style that is neither "+" nor ✓, with an accessible label such as "Checking saved state". Then each button becomes active as "+" or ✓.
4. If the saved-state request fails, the buttons stay inactive and the page shows a short inline retry message. The translation stays visible.
5. Saving or unsaving updates the saved-state cache for that sense directly, so the buttons don't refetch. This applies wherever it happens, including deleting a card on My Words.
   - The lookup query doesn't depend on the user, so nothing invalidates it any more: not saving or unsaving, and not signing in or out. Today `useCards` and `useAuth` invalidate `['dictionary']`.
   - The saved-state query key includes the signed-in user's id, so one user's saved state is never shown to another after a sign-out and sign-in in the same tab. Signing out removes the saved-state queries.
6. Signed-out users never request saved state. Their buttons are active "+" immediately, and clicking one leads to sign-in, as today (`web/src/pages/SearchPage.tsx`). This is a proposal: Alex said the buttons are inactive until the saved state arrives, and signed-out users have no saved state to wait for.
7. Card views (`MyWordsPage`, `SenseCard`) use the neutral field names. User-facing text that names the languages (e.g. the empty state in `SearchPage.tsx`, "Search for English words to see Russian translations") is presentation, not code structure. Proposal: keep it as text for now, and don't derive it from the language codes until there is a second pair.
8. The MSW mock layer (`web/src/mocks/`) follows the same split: a user-independent lookup handler, a separate saved-state handler with the same 401/422 rules, and fixtures with the neutral names and language fields.

## 4. Consumers to update
Code that uses today's names or the user-dependent lookup. Found with `git grep` for `translation_ru|synonyms_ru|meanings_en|saved|card_id|lang` when this section was written; re-run it during implementation.
- Backend main code:
  - `provider/`: `ExampleSentence`, `ProviderSense`, `SenseIds`, `DictionaryProvider`, and the fake provider with its `fake-data/*.json`.
  - `persistence/`: `Example`, `LexemeEntity`, `SenseEntity`, `CardEntity`, `LexemeRepository`.
  - `service/`: `SenseCatalog`, and `LookupService`, which loses its `CardService` dependency and its saved-state map. `CardService.savedCardIds` stays and serves the new endpoint.
  - `api/`: `Dtos`, `DictionaryController`, `CardController`, `ApiExceptionHandler`.
- Backend tests:
  - `DictionaryApiTest`: the renames (including its raw `lexemes` insert); its saved/card_id assertions move to a new saved-state test, and it gains the cache-header test.
  - `SenseIdsTest`: the expected hashes change.
  - `CardApiTest`: the renames (including its raw SQL, whose `INSERT INTO user_cards` must also supply the new `NOT NULL` columns `source_lang` and `target_lang`), and its lookup saved/card_id assertions move to the new saved-state test too: the save, delete and per-user isolation flows read them from the lookup today.
  - `FakeDictionaryProviderTest` and `OpenApiContractTest`: the renames.
- Web:
  - `api/`: `schema.d.ts` (regenerated), `types.ts` (add `SavedSense`), and `client.ts` with `client.test.ts` (the new `listSavedSenses`).
  - `hooks/`: `useDictionary.ts`, `useCards.ts`, `useAuth.ts`.
  - Views: `pages/SearchPage.tsx`, `pages/MyWordsPage.tsx`, `components/SenseCard.tsx` and its test, `App.test.tsx`.
  - Mocks: `mocks/handlers.ts`, `mocks/fixtures.ts`.

## 5. What changes outside the code
- `docs/api/openapi.json`: re-exported, never hand-edited. `web/src/api/schema.d.ts` is regenerated (`npm run gen:api`).
- `docs/plan.md` (data model, Step 2 endpoint list), `backend/intent.md`, `web/intent.md` and `docs/engineering.md` (where they name fields or describe the lookup) are updated to match.
- The e2e test, if one exists by then (task `e2e-smoke`), still passes.
