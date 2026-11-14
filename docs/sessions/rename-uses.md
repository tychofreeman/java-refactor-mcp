# rename: one loop for fields, methods and types; "1 occurrence"

- **Item:** issue #513 (code review of 2026-11-13, moves #8), group 4 of tracking issue #532
- **Date:** 2026-11-14
- **Branch:** rename-uses

## Plan

Reproduce the plural first: a field nothing uses, and a parameter nothing uses, each renamed,
should say `1 occurrence`. Fix with one `Names.plural` at the four format strings. Then, green,
extract the loop the three member renames share into `renameUses(predicate, per-file check, moved
file)` and let each keep its own checks; map offsets back with `NameCheck.before` and delete
Rename's own `before`.

## Notes

- plan: both new goldens failed with `1 occurrences`.
- read: `inspect` of `Rename` (outline, then 380-1028), `names` for `Names`, `Holding`,
  `NameCheck.before`, `NameCheck.describe`, `Rename.describe`, the `plural` copies.
- refusal: none.
- tried: `extract_function` of `renameType`'s loop applied (a `RenameUsesResult` of five locals)
  but fixed the predicate and the file move; undone (friction log).
- hand: none in Java; goldens, docs.
- `describe` wording left: unifying it changes refusal text, and #501 (one label format) is
  another agent's.

## Measured

Not measured: the session-log hook is not loaded here (the CLI log is, `JREFACTOR_LOG`). Five
tool calls wrote Java (two batches of 4-5 steps, one `replace_text`, `create_file`,
`add_declaration`), one undone `extract_function`.

## Assessment

Through the tools; the helper and the three call sites were typed as content, since no
refactoring parameterizes a body by a predicate.

## Follow-ups

- #511: `MoveType.plural`, `SessionReport.plural` and the inline ternaries onto `Names.plural`.
- #501: `Rename.describe` and `NameCheck.describe` name a field differently.
