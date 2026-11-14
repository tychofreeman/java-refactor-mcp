# SubclassToStrategy.run in parts (#528, part 2)

- **Item:** issue #528 (code review, group 5 of #532), the moves finding: `SubclassToStrategy.run`,
  about 390 lines that check, name, write the superclass and rewrite every use.
- **Date:** 2026-11-14
- **Branch:** strategy-run-split

## Plan

`extract_function` of the parts the review names, each a whole block of `run`: the check that no
class extends the subclass (`refuseSubclasses`), the names (`names`, its four results in a record),
the constructors' edits (`constructorEdits`) and the uses' edits (`useEdits`). Then `rename` of the
record the tool names `NamesResult` to `Names`, and `inline` of the locals it was unpacked into, so
the helpers take the record.

## Notes

- extract_function: `refuseSubclasses` (3 parameters).
- hand-free mistake: a second `extract_function` used the line numbers read before the first, which
  had moved them; `undo`, read again, extract again. Extract from the bottom up, or reread between.
- extract_function: `names` (5 parameters, 4 results as `NamesResult`); `rename` to `Names`, its
  local to `naming` (`names` was taken; the rename said so); `inline` of `cap`, `subFn`, `ownFn`,
  `field` in one batch.
- extract_function: `useEdits` (11 parameters) and `constructorEdits` (8), bottom first. The tool
  said "fewer lines may read better"; these are the blocks the review asked for, and their
  parameters are the subclass's and superclass's facts. A context record for those is a follow-up.
- refusal: `extract_variable` of the result message (`"turned " + ... + " deleted)"`) without its
  last operand (`+ written.said()`): the prefix of an extended `+` is no expression node. Left as is.

## Measured

Not measured (no session-log hook). 4 `extract_function`, 1 `undo`, 2 `rename`, 4 `inline`; no edits.

## Assessment

Entirely through refactorings. `run` keeps the order of what it does and reads in four named steps;
the two edit-building helpers have long parameter lists, which a context record would shorten
(listed on #528). `subclass_to_strategy` goldens and its probe pass unchanged.

## Follow-ups

- A record of the subclass's and superclass's facts for `useEdits` and `constructorEdits`.
