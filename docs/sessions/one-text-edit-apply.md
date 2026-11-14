# One TextEdit.apply that rejects overlaps

- **Item:** issue #498 (code review of 2026-11-13, core #4 and control-flow #10), roadmap #350,
  group 3 of tracking issue #532
- **Date:** 2026-11-14
- **Branch:** one-text-edit-apply

## Plan

Red: a `TextEditTest` that overlapping edits throw. Green: the check in `TextEdit.apply`.
Then, green, route the hand-written appliers through it: `ConvertLoop.Edit` renamed onto
`TextEdit` and deleted, its loop and `within` replaced by calls (then `inline`); `ParameterValue.Swap`
(offset, length) rewritten as `TextEdit` (start, end); `InlineFunction.substituted`'s window loop
as `TextEdit.apply` with an offset; `Imports.fix`'s `int[]` cuts and inserts as `TextEdit`s;
`MoveType.apply` inlined. Drop the sorts before `TextEdit.apply`, which sorts itself.

## Notes

- plan: the full suite passed with the check in place before any applier moved: no tool relied on
  overlapping edits.
- refusal: `rename` of `ConvertLoop.Edit` to `TextEdit` was applied (the nested record then
  shadowed the top-level one); `replace_declaration` deleting it was refused, as its uses bound to
  it. Deleted by `replace_text` in the same batch as the applier's replacement: the gate checks
  the uses against the top-level `TextEdit`, the same shape.
- refusal: `inline` of `ConvertLoop.within` refused `List.of()` as an argument that "calls"; the
  call with no edits was `replace_expression`'d to `source(text, body)` (what it computes), then
  `inline` took the other two.
- refusal: `replace_expression` with `old` (it takes `expression`); `replace_text` of `List<Swap>`
  without `all_occurrences`, fixed by `batch` `retry`.
- plan: `Imports.fix` deleted a line twice when two removed imports shared it (`import a; import
  b;`), cutting the next line too, which the gate then refused; now the cut is one, and two
  identical cuts would have thrown. Golden `imports_removed_one_line`.
- plan: `MoveMember` shifted its edits into a window by hand; it passes the window's offset now.

## Measured

Not measured: these runs were made before `JREFACTOR_LOG` was set. Counted from the notes:
`create_file` 1, `replace_declaration` 1, `rename` 1, `inline` 2, `replace_expression` 1,
`replace_statements` 2, batches 4 (21 steps); no Java written by hand.

## Assessment

Through the tools: one rename and two inlines (refactorings), the rest edits. The plan held.

## Follow-ups

- `Imports.fix` cuts the whole line of a removed import, also when another import on that line
  stays (the gate then refuses; not silent).
- `MemberMove.gate`'s sort before `TextEdit.apply` goes with #497.
