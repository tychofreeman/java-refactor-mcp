# The design routine on Java: an A/B run

- **Item:** the user, 2026-11-19, from "what's next?": measure step 1's design routine (#691,
  gorefactor's cost-history 35) on Java, where it had not been run.
- **Date:** 2026-11-19
- **Branch:** ab-design-routine

## Plan

The question: does writing down the easy change, the places today and the refactorings make a
model reshape the Java code before adding the feature, as it did on Go, and at what cost?

- Control: `eval-tasks/claude-md-before-routine/mcp.md`, `eval-tasks/claude-md/mcp.md` as it was
  before #691 (`637290e2`: "If the change would be hard, refactor first"); `manual.md` copied, as
  the harness takes a directory. Variant: `eval-tasks/claude-md/mcp.md` now.
- Tasks: `shop.json`, all four. `shop-tangled` is gorefactor's `design-tangled` (the customer
  kind added to `Pricing.total`), `shop-duplication` the three copied exports (the shape of
  `freight-air`); `shop-currency` and `shop-address` change a signature or a record and have
  less to reshape, so they are near the negative control.
- gorefactor's harness from its checkout, Sonnet, the mcp arm only (the manual arm's
  instructions did not change), 3 runs a task and variant: 24 runs, about $5. Both variants the
  same day, from this branch with no change to `src/main`.
- Graded as section 35 was: cost, turns, pass, and the level reached before the feature, from
  each run's diff: in `shop-tangled` whether the kinds' rules got one home (function or design)
  or a branch per kind; in `shop-duplication` whether the three exports' filter and sort became
  one helper or were changed three times. And which tools carried the reshaping.

No Java of this project changes; the record and the control file are the work.

## Notes

- plan: the two arms were meant to run at once; the control's command lost its directory
  variable (`$J` was set inside the backgrounded first command), so it ran after the routine's,
  the same afternoon, from the same commit.
- plan: the harness's quality columns (complexity, duplicated lines) measure Go only and read 0
  here, so the level was graded from each run's tool calls (`eval-runs/ab-design-routine/*`).
- No Java was written in this session; the runs' own Java went through the fixture copies.

## Measured

Sonnet, mcp arm, 3 runs a task and variant, from `efb36a2b`; `eval-runs/ab-design-routine/
{routine,control}`. All 24 passed. Total $2.73 (routine) and $2.37 (control).

| task | routine: cost · turns · diff lines | control |
|---|---|---|
| shop-duplication | $0.27 · 33.0 · 87 | $0.25 · 29.0 · 91 |
| shop-tangled | $0.14 · 12.7 · 4 | $0.12 · 13.0 · 5 |
| shop-currency | $0.26 · 24.3 · 54 | $0.17 · 17.3 · 60 |
| shop-address | $0.24 · 22.7 · 80 | $0.25 · 20.3 · 76 |

Level reached before the feature:

| task | routine | control |
|---|---|---|
| shop-duplication | design, 3/3: the filter and sort extracted (`extract_function`), moved to a class of its own (`move`), the other two copies replaced by calls | design, 3/3, the same operations |
| shop-tangled | none needed, 3/3: one `else if` in the unit-price chain, the tax rule already right ("with only one place to change, I did no refactoring first") | the same, 3/3 |
| shop-currency | one formatter, 3/3, reached by refactoring: the existing dollars formatting made a helper and `fold`ed into its other copy, then changed | one formatter, 3/3, written: `Currency.format` added and each export's formatting replaced |
| shop-address | `extract_type` of Order's components in 2/3, edits in 1 | `extract_type` in 3/3 |

`bin/session-report` for this session: 38 tool calls, 1 jrefactor edit step (the record
lines are docs), no Java by hand, no refusals.

## Assessment

- **The routine changed nothing measurable on these tasks.** The old step 1 already took the
  duplicated exports to one home in every run; Java's `shop-tangled` has one place for the new
  kind (unlike Go's `design-tangled`), and both arms saw it. The routine cost about 15% more a
  run, most of it on `shop-currency`, where it reshaped the existing formatting through the
  tools before changing it, where the control wrote the new method directly. That is the only
  difference in kind: the routine's sessions refactored (extract, fold), the control's edited.
- **The shapes are missing, not the effect.** Go's gain was on `lending-magazine` (five switches
  on one kind) and `freight-air` (eight Road/Rail copies), which this repo has no port of. Until
  one runs, the routine stays in `examples/CLAUDE.md` on Go's evidence alone.
- **Through the tools:** in every `shop-duplication` run, both arms, the other two copies were
  replaced by `replace_statements` with a call typed out: `fold` refused the extracted method in
  5 of 6 runs ("returns a value from statements"), and the sixth did not try it. gorefactor's
  fold takes such a body since its #694 and #695: roadmap 529.
- **One change that would have helped:** `fold` of a method that returns after statements
  (roadmap 529).

## Follow-ups

- Port gorefactor's `lending-magazine` and `freight-air` to Java and run the two variants on
  them: those are the shapes where the routine moved Go sessions.
- Roadmap 529, `fold` with returns. The other parity gaps found the same day (gorefactor's
  `replace_expression` `everywhere` and holes, #676 and #690; edits through a symlink escaping
  the project, its #660, which reproduces here) wait for the user's choice.
