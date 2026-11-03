# A rename column below 1 is refused

- **Item:** roadmap order 37 (review row 44): "A column of 0 or below is
  accepted silently and falls back to name lookup, so `Positions.offset`'s
  refusal is never reached." Chosen by the user on 2026-11-03 ("Do all 4 in
  order": 24, 37, 35, 40).
- **Date:** 2026-11-03
- **Branch:** refuse-column-below-one

## Plan

`Tools` used 0 for "no column", so a given 0 (or a negative) was
indistinguishable from none. The change: `Tools` passes the column as it is,
`null` when absent; `Rename.run` takes an `Integer`, refuses a column below 1
with a reason, and tests for `null` instead of `> 0`.

Operations, in order:
1. `rename_refused_column_zero` golden (red: column 0 falls through to name
   lookup and gets the clash refusal, not the column refusal).
2. `Tools.rename` passes `args.integer("column")` (null when absent).
3. `Rename.run` refuses a non-null column below 1, then tests `column != null`.

The one behaviour change: a column of 0 or below is now refused, with the
wording "column starts at 1; got N. Give the 1-based column of the name, or
leave column out." Before, it was ignored.

## Notes

- `read:` `Tools.rename`, `Rename.run` (the column test), and `Positions.offset`
  (its refusal text names "line and column start at 1"), to pick the wording.
- `hand:` `Tools.java`, `Rename.java` and the golden case (copied `in/` from
  `rename_refused_clash`) were written with the Edit tool and cp. The
  java-refactor-mcp tools were not in this session's tool list. Logged in the
  friction log.
- `refusal:` none.

## Measured

Not measured. No session log for this branch. Verify: 145 tests, 0 failures,
2 opt-in cost tests skipped. The red golden case failed first, with the
clash refusal, as described above.

## Assessment

- **Through the tools or around them:** around; a golden case and two source
  edits, all by hand.
- **Did the plan hold?** Yes. The red case failed for the stated reason.
- **Refusal wording:** new; it is a model-facing refusal, so it names the fix
  (leave the column out).
- **One change that would have helped:** the java-refactor-mcp tools in the
  session.

## Follow-ups

- The tool description for `column` is unchanged; it still says "Optional
  1-based column of the name".
