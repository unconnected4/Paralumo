---
task: <short-slug>                # stable ID, also used in the commit trailer "Task: <short-slug>"
title: <one-line task title>
status: in-progress               # in-progress | ready-for-review | blocked | done | abandoned (ready-for-review: an implementer found nothing left; see docs/orchestration.md)
branch: <branch>
started: <UTC, YYYY-MM-DDTHH:MM:SSZ>
last_updated: <UTC, YYYY-MM-DDTHH:MM:SSZ>
last_commit: <sha of HEAD when this file was last updated>
agents: []                        # agent names (see AGENTS.md) of everyone who worked on this task, e.g. [ClaudeD-Fable5.1]
related_decisions: []             # links to intent.md sections or docs/ files
---

# <Task title>

## Goal and acceptance
What "done" means, and how it's verified (tests, manual check).

## Current state
A few sentences: where things stand right now. This is what the next agent reads first.

## Done
- <what was completed>, <commit sha(s)>

## Tried and rejected
- <approach>: <why it was dropped>, <evidence: test output, error, doc link>

## Decisions made during this task
- <decision>: <reason>, <alternatives considered>
- <decision> (by Alex): <reason as Alex gave it>

## Open questions
<!-- Tags "For Alex:" and "by Alex" are defined in AGENTS.md "Working with Alex". Keep answered questions; copy lasting answers to Decisions. -->
- <question>, <who should answer: next agent>
- For Alex: <question>, <why only Alex can answer, what it blocks>
- ~~For Alex: <question>~~ → <answer> (by Alex, <UTC date>)

## Next step
The single most useful thing for the next session to do.

## Session log
<!-- Append one line per session. Never rewrite earlier lines. -->
- <UTC timestamp> <agent name>: <one-line summary>, commits <from-sha>..<to-sha>

## Outcome
<!-- Filled in only when archiving. -->
- Result: <what shipped / why abandoned>, final commit or PR <ref>
- Promoted to durable docs: <which decisions moved to intent.md / docs, with links>
- Retrospective: <what went well or badly in the process: misunderstandings, rework, handoff problems>
