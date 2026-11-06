# Backend: agent instructions

Spring Boot JSON API in Java. Intent and decisions: [intent.md](intent.md). Shared conventions: [../docs/engineering.md](../docs/engineering.md).

## Prerequisites
- JDK 25 (Temurin), with `JAVA_HOME` set. Gradle itself comes from the wrapper, so don't install it globally.
- On Windows use `.\gradlew.bat` (PowerShell) or `./gradlew` (Git Bash). Both are the same wrapper.

## Commands (run from `backend/`)
- Run API: `./gradlew bootRun` → http://localhost:8000 (Swagger UI at `/swagger-ui.html`). Reads `backend/.env`.
- Unit tests: `./gradlew test`
- Integration tests (needs `docker compose up -d` at repo root): `./gradlew integrationTest`
- Format: `./gradlew spotlessApply`. Check only: `./gradlew spotlessCheck`
- Everything a commit needs (compile with Error Prone + NullAway, format check, unit and integration tests): `./gradlew check`
- Migrations run automatically (Flyway) when the app or an integration test starts. New migration: add `src/main/resources/db/migration/V<n>__<what_changed>.sql`.
- Export API spec: `./gradlew exportOpenApi` → writes `../docs/api/openapi.json`. It boots the app, so it needs Postgres like the integration tests. `check` fails if the committed spec is out of date.

## Layout
```
build.gradle.kts, settings.gradle.kts, gradle/libs.versions.toml (all dependency versions), gradlew, gradlew.bat
src/main/java/com/paralumo/
  ParalumoApplication.java   entry point
  config/        @ConfigurationProperties records (the only place env vars are read), security, Jackson, request ID + logging
  api/           @RestControllers, thin: validate, call a service, return a DTO. DTO records, error handling
  service/       business logic (cards, srs, lookup)
  persistence/   JPA entities and Spring Data repositories
  provider/      DictionaryProvider + fake/ (+ yandex/ later)
src/main/resources/
  application.yaml
  db/migration/  Flyway SQL migrations
  fake-data/     <word>.json, hand-authored data for the fake provider
src/test/java/com/paralumo/   unit tests; integration tests are tagged @Tag("integration")
src/test/resources/fixtures/  recorded provider JSON for integration tests
```

## Rules
- Every entity change ships with a Flyway migration in the same commit. Migrations are forward-only SQL, never edited once committed, and must be backward-compatible with the previous release.
- Any change to controllers or DTOs means re-exporting `docs/api/openapi.json` in the same commit.
- New config goes into a `@ConfigurationProperties` record and `.env.example` together. Never add secrets to code, `application.yaml` or fixtures.
- Tests use `DICTIONARY_PROVIDER=fake` and never call Yandex.
- Every endpoint that touches user data has a per-user isolation test.
- Compiler warnings are errors (`-Werror`), and Gradle deprecation warnings fail the build (`org.gradle.warning.mode=fail`). Fix them, don't suppress them.
