---
task: alex-feedback
title: Collect Alex's feedback on the project, then turn it into instructions
status: in-progress
branch: main
started: 2026-11-18T10:44:30Z
last_updated: 2026-11-18T10:44:30Z
last_commit: c7d8352
agents: [Claude-Opus5.5-Interactive]
related_decisions: []
---

# Collect Alex's feedback on the project, then turn it into instructions

## Goal and acceptance
Alex gives feedback point by point. Each point is recorded here as given, tagged `by Alex`, with no changes to instructions or code yet. When Alex says to compose, the points become changes to `AGENTS.md`, `intent.md`, `docs/` and handoff files, and then Alex starts the orchestrated implementation cycle.

Done when every point below is either composed into instructions (with links) or explicitly dropped by Alex.

## Current state
Collecting. Nothing composed yet. Agents: don't act on these points until they are composed.

## Feedback (by Alex)
<!-- One entry per point: what Alex said, the evidence, how far it reaches, and its composed status. -->

### F1. Language pair is baked into the code structure
- **Said (by Alex):** "Lexems are linked to Russian in code, it is definitely incorrect. Russian can be hardcoded as target language for now, but it should not be in the code structure." Example given: `backend/src/main/java/com/paralumo/provider/ProviderSense.java` with `translationRu`, `synonymsRu`, `meaningsEn`.
- **Meaning:** names, types and schemas must be language-neutral (e.g. `translation`, `synonyms`, `meanings`, with the languages carried as data or configuration). Using `ru` as the only target value for now is fine.
- **Reach found at c7d8352** (files with `Ru`/`En`/`_ru`/`_en` names): backend provider, service, persistence entities, DTOs, Flyway migration V1, fake data JSON, backend tests; `docs/api/openapi.json`; web generated schema, `SenseCard`, `MyWordsPage`, MSW fixtures and handlers, tests; `docs/plan.md` data model.
- **Status:** collected, not composed.

## Done
- Nothing yet.

## Tried and rejected
- Nothing yet.

## Decisions made during this task
- None yet.

## Open questions
- None yet.

## Next step
Keep collecting until Alex says to compose.

## Session log
- 2026-11-18T10:44:30Z Claude-Opus5.5-Interactive: Started the feedback collection; recorded F1.

## Outcome
<!-- Filled in only when archiving. -->
