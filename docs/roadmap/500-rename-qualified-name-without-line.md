---
order: 490
item: 500
status: Done
---

## Item

`rename` with `name` as `Type.member` (or `Type.method(params)` for an overload) needs no
`line`: it was refused with "rename needs line" even when the name picked one declaration, and
inside a `batch` an earlier `move` decides the line, which the model cannot know when it writes
the batch.

## Status

Done 2026-11-15: `line` is optional in `rename`'s schema; without it, `Rename.run(project, file,
name, newName)` finds the declaration with `Declarations.find` (a syntax parse of `file`) and
renames from its name's line and column. A name with no type still needs `line`; an ambiguous
overload is refused listing each, as `Declarations.find` refuses for the other tools.

## Evidence

Issue #613; friction log (`ChangeSignature.pure`, `Hierarchy.ancestors`, `Indent.reindented`,
`Nodes.ancestorAbove`, two in batches). Goldens `rename_qualified_field_no_line`,
`rename_qualified_method_no_line` (calls in a second file), `rename_qualified_overload_no_line`,
`rename_qualified_refused_overloads`, `rename_refused_no_line`, `batch_move_then_rename_qualified`.
