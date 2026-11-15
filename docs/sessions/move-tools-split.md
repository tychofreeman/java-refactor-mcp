# EncapsulateField and MoveMember: records for their context (#528, item 6, part 1)

- **Item:** issue #528, item 6 of its last comment (the long move tools), first part:
  `EncapsulateField.run` (160 lines) and `useEdit` (8 parameters), `MoveMember.run` (227 lines)
  and `rewrite` (10). `MoveToPackage` and `InlineClass` follow in their own PR.
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
- MoveMember: `extract_type` of `rewrite`'s seven adjacent context parameters into `MovedMethod`
  (its own file); the construction it wrote inside the call loop was hoisted by
  `insert_statements` and `replace_statements`, and the record's `public` and doc by
  `replace_text`.
- MoveMember: extract_function of `isPrivate`, then of the text move (10 parameters: `undo`, with
  the extraction after it, which was redone), `reportNullable` (5), `refuseOverriders` (5). The
  call-site visitor stays in `run`: extracted, it would take 8.

## Measured

Not measured (no session-log hook). 9 `extract_function` (one undone), 2 `undo`, 1
`extract_type`, 2 `rename`, 6 `inline`, 1 `add_parameter`, 4 `remove_parameter`, 5 edits
(`replace_text`, `insert_statements`, `replace_statements`); no Java by hand.

## Assessment

Through refactorings. `EncapsulateField.run` 160 -> about 100 lines, `useEdit` 8 -> 5
parameters; `MoveMember.rewrite` 10 -> 4 parameters, `MoveMember.run` 227 -> about 195 lines
(the call-site loop is its bulk, with too much context to extract well). The parameter
swap (add the record, remove each value with `value`) is a composition the friction log names.

## Follow-ups

- `MoveToPackage.run`, `InlineClass.run`; `MoveMember.run`'s call-site loop.
