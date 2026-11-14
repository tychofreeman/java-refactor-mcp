---
order: 340
item: 350
status: Done
---

## Item

Text edits were applied by four hand-written appliers besides `TextEdit.apply` (`ConvertLoop.Edit`, a clone of `TextEdit`, and its `within`; `ParameterValue.Swap`; `InlineFunction.substituted`'s window; `Imports.fix`'s `int[]` cuts and inserts) and a wrapper (`MoveType.apply`), and none checked for overlapping edits (issue #498, code review)

## Status

Done 2026-11-14: `TextEdit.apply` throws `IllegalStateException` on overlapping edits (`TextEditTest`); the appliers call it (a window by its `offset`), `ConvertLoop.Edit`, `within`, `ParameterValue.Swap` and `MoveType.apply` are gone, and the sorts before it are dropped. `Imports.fix` deleted a line twice when two removed imports shared it, cutting the next line (refused by the gate): one cut now, golden `imports_removed_one_line`

## Evidence

Issue #498; `docs/sessions/one-text-edit-apply.md`
