---
order: 499
item: 509
status: Done
---

## Item

`rename name=Type.field` without `line` renames one field of a statement that declares several (`int a, b;`), as `rename` with `line` does (issue #642)

## Status

Done (2026-11-15): `Declarations.find` returns the statement and leaves the refusal of a shared statement to `replace_declaration` (`Declarations.replace`); `Rename.declaredName` takes the fragment named. Goldens `rename_qualified_field_fragment_no_line`, `replace_declaration_refused_shared_field`

## Evidence

Issue #642, found by the agent that fixed #613 (PR #630): the refusal named `replace_text`, which would change only the declaration, not its uses
