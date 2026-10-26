{common}

Your role in this round: **reviewer**. The implementers ({implementers}) have stopped because none of them found anything left to do, or because one of them set `blocked`. You decide whether the task is done, needs another implementation cycle, or needs Alex.

1. Questions first. Go through every open `For Alex:` line in the handoff. For each one, try to resolve it yourself from `intent.md`, `docs/`, the handoff history and the code. If you can, append the answer to that line as `→ <answer> (resolved by {agent}, <UTC date>: <evidence>)` and treat it as settled for this review. Never tag your own answer `by Alex`. Leave open only what genuinely needs Alex (intent, priorities, accounts, taste), and say what it blocks.
2. Find what to review: the commits of this task since the previous review round (see the handoff's Session log). If there was no previous review, review all commits of the task: `git log --grep="Task: {task}"`.
3. Check the work against the handoff's "Goal and acceptance", the project's `AGENTS.md`, `intent.md` and `docs/`. Run the project's lint, type checks and tests yourself; don't trust the log. Check that decisions made by the implementers are recorded, and that nothing an earlier implementer did was undone without a recorded reason.
4. Small, unambiguous fixes (a typo, a missing test, a stale doc line) you may commit directly and record under Done. Anything larger goes into the handoff as a numbered list of findings at the top of "Next step", each with the file, what is wrong, and what "fixed" looks like. Findings are for the implementers, so be concrete.
5. Set `status` in the handoff front matter:
   - `in-progress` when there are findings. The implementers start a new cycle from the top of "Next step".
   - `blocked` when the task can't continue without Alex: one or more `For Alex:` questions are still open and block the work. The loop stops and Alex reads the handoff.
   - `done` when the acceptance criteria are met, you have no findings, and nothing open blocks. Then finish the task per `AGENTS.md`: fill in Outcome, copy lasting decisions to `intent.md` or `docs/`, and archive the handoff with `git mv` into `docs/handoff/archive/`.
6. Commit everything, including the handoff file (or its archived copy).
