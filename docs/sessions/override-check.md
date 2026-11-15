# Extraction's override analysis as OverrideCheck; ConvertToRecord.run in parts (#528, item 8, part 1)

- **Item:** issue #528, item 8 of its last comment, first part: `Extraction`'s override analysis
  as its own class, and `ConvertToRecord.run` (187 lines), the first of the control-flow long
  methods.
- **Date:** 2026-11-15
- **Branch:** override-check

## Plan

`create_file` of an empty `OverrideCheck`, then `move` of `keys`, `takenOver` (with `callsSuper`,
which only it calls) and `checkOverride` to it. ConvertToRecord: `extract_function` of the header,
the body's brace, the accessors made public and the missing value methods, bottom first.

## Notes

- refusal: `move` of `checkOverride`: it uses `refuseEnclosing` and `overridden`, private to
  `Extraction`; the refusal named both fixes. `replace_text` of the two modifiers (package-private),
  then the moves in one batch.
- `create_file` was sent `path` (refused, names `file`); the `retry` would have lost the `@@`
  section, so the batch was resent whole (as the 2026-11-15 inspect-side-effects entry says).
- `move` widened each moved method to package access; `replace_text` made `keys`, `callsSuper`
  and `takenOver` private again in `OverrideCheck`, and fixed the doc's `{@link #overridden}`.
- ConvertToRecord: `header` (4 parameters), `bodyOpen` (2), `publicAccessors` (4), `missing` (3).

## Measured

Not measured (no session-log hook). 1 `create_file`, 3 `move` (one took `callsSuper` along), 4
`extract_function`, 6 `replace_text`; no Java by hand.

## Assessment

Through the tools. `Extraction` 995 -> about 810 lines; `checkOverride` now takes the
`Extraction` as `receiver` and sets its `overridden`. `ConvertToRecord.run` 187 -> about 120 lines.

## Follow-ups

- `checkOverride` could return the overridden method rather than set the field.
- The rest of item 8: `ExtractType.fields`/`components`, `Behavior.checkCrossed`/`checkExits`,
  `TypeSwitch.run`, `EnumSwitch.method`, `ConvertLambda.run`, `ConvertLoop.run`.
