# EncapsulateField.run in parts, its facts as a record (#528, item 6, part 1)

- **Item:** issue #528, item 6 of its last comment (the long move tools), first part:
  `EncapsulateField.run` (160 lines) and `useEdit` (8 parameters). `MoveMember`,
  `MoveToPackage` and `InlineClass` follow in their own PRs.
- **Date:** 2026-11-15
- **Branch:** move-tools-split

## Plan

As in change-signature-run: `extract_function` of the lines that compute the field's facts (six
results, so a record), `rename` it `Accessors`, `inline` its locals; then `add_parameter` of the
record to `useEdit` and `remove_parameter` with `value` of the four values it carries. Then
`extract_function` of the accessor text, the modifier edits and the field lookup.

## Notes

- refusal: `remove_parameter` of `fieldKey` with `value` `accessors.fieldKey()` before the locals
  were inlined: "the call ... passes fieldKey, not accessors.fieldKey()". Inlining first (one
  batch: rename, six inlines, add_parameter, four remove_parameter) went through.
- `remove_parameter` leaves each removed value as a local at the start of `useEdit`
  (`String getter = accessors.getter();`): kept, as the body reads them often.
- extract_function: `accessorText` (3 parameters), `makePrivate` (2), `declaration` (5).

## Measured

Not measured (no session-log hook). 4 `extract_function`, 2 `rename`, 6 `inline`, 1
`add_parameter`, 4 `remove_parameter`, 1 `replace_text` (the record's doc); no Java by hand.

## Assessment

Through refactorings. `run` 160 -> about 100 lines; `useEdit` 8 -> 5 parameters. The parameter
swap (add the record, remove each value with `value`) is a composition the friction log names.

## Follow-ups

- `MoveMember.run` and `rewrite`, `MoveToPackage.run`, `InlineClass.run`.
