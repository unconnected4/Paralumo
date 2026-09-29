{common}

Your role in this round: **reviewer**.

1. Find what to review: the commits of this task since the previous review round (see the handoff's Session log). If there was no previous review, review all commits of the task: `git log --grep="Task: {task}"`.
2. Check the work against the handoff's "Goal and acceptance", the project's `AGENTS.md`, `intent.md` and `docs/`. Run the project's lint, type checks and tests yourself; don't trust the log.
3. Small, unambiguous fixes (a typo, a missing test, a stale doc line) you may commit directly and record under Done. Anything larger goes into the handoff as a numbered list of findings at the top of "Next step", each with the file, what is wrong, and what "fixed" looks like. Findings are for the implementer, so be concrete.
4. Set `status` in the handoff front matter:
   - `in-progress` when there are findings for the implementer.
   - `blocked` when the task can't continue without Alex (question recorded with `For Alex:`).
   - `done` when the acceptance criteria are met and you have no findings. Then finish the task per `AGENTS.md`: fill in Outcome, copy lasting decisions to `intent.md` or `docs/`, and archive the handoff with `git mv` into `docs/handoff/archive/`.
5. Commit everything, including the handoff file (or its archived copy).
