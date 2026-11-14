# One home for "the file that declares a type"

- **Item:** issue #482 (code review, tracking issue #532 group 3): seven lookups of a type's file,
  `TypeSwitch.fileOf`'s suffix match picking the wrong one.
- **Date:** 2026-11-13
- **Branch:** file-declaring-type

## Plan

Reproduce the wrong file with a golden case of two same-suffix packages, make `fileOf` resolve the
implied path, then refactor while green: extract the lookup, move it to `SourceProject`, and point
each copy at it, inlining the delegates.

## Notes

- read: issue body; `inspect` with `names` for the seven sites.
- red: `inline_class_same_suffix` refused ("Address is used in com/example/Address.java") because
  the decoy `a/com/example/Address.java` sorts first.
- green: `replace_statements` in `TypeSwitch.fileOf`.
- refactor: `extract_variable`, `extract_function`, batch (`inline` local, `move_parameter`).
- refusal: `move` to a record (`SourceProject`); hand composition in one batch (friction log).
- refactor: `move` of `ByLine.topLevel` to `Names`, `rename` to `outermost`.
- refactor: `SourceProject.fileOf` added; `TypeSwitch.fileAround` made a delegate and `inline`d.
- refusal: `inline` of `Stubs.Round.sourceOf` (member class); `replace_text` all occurrences.
- red: `move_member_qualified_same_suffix` refused "several files declare Tax" for a qualified `to`;
  green with `fileDeclaring` in `MoveMember.targetFile`, then the import fix refused the ambiguous
  `Tax`: the named class is now the chosen import (`insert_statements`).
- edits: Signatures, Imports, Rename, ChangeSignature, Jdt, Fold, MoveToPackage, Inspect by
  `replace_statements` in two batches.

## Measured

Not measured (no session-log hook here). About 20 tool calls: 9 refactorings (extract x2, inline
x3, move_parameter, move, rename, plus delegates), 2 refusals, the rest edits; no Java by hand.

## Assessment

Through the tools. The two gaps (a record as a move target, inline of a member class's method)
are in the friction log.

## Follow-ups

- `ChangeSignature.referenced` still adds a single-file parse to `family.sources` (the two-parse
  trap the review names); comparisons there are by key.
