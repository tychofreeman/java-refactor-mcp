# Member enums and member case types in `type_switch_to_method`

- **Item:** roadmap #154 (order 147): slice 3 of `docs/sessions/enums-sketch.md`, increment 8
- **Date:** 2026-11-13
- **Branch:** enum-switch-nested (from enum-switch-yield, #399, before it merged)

## Plan

`TypeSwitch.fileOf` and `declaration` take top-level types only, and enums are often members.
Add `TypeSwitch.fileAround` (the file of a member type's outermost type, null for a local or
anonymous class) beside `fileOf`, which other tools rely on staying top-level, and make
`declaration` find a type by its name with its enclosing types, walking the unit, so the method
form's second parse (without bindings) finds a member enum too. Use them in `TypeSwitch.run` and
`EnumSwitch`. Red first: goldens `type_switch_enum_nested` (a member enum at a four-space step,
the switch in another file, method form), `type_switch_enum_nested_field` (the switch in the
enum's own outer class, field form) and `type_switch_nested_case_type` (records nested in a sealed
interface, the type form).

## Notes

- read: `fileOf`'s callers by `inspect` with `pattern` (project-wide grep for `TypeSwitch.fileOf`
  by hand, `grep -rn`).
- bulk: one batch of nine: four call sites to `fileAround`, `replace_declaration` of
  `TypeSwitch.declaration`, `add_declaration` of `fileAround`, and `EnumSwitch`'s file, wording and
  `declaration` (now a cast of `TypeSwitch.declaration`).
- plan: the two enum goldens passed at once; `type_switch_nested_case_type` failed on layout: a
  one-line member record opened up by `MemberMove.appendTo` got its closing brace at column 0 (the
  fault #141 fixed in `afterConstants`, in `appendTo`'s other two branches). Fixed by one batch of
  two `replace_text`: the brace takes the indentation of its own line.
- hand: nothing in Java.

## Measured

Not measured: the session-log hook is not loaded. From the notes: 2 tool calls that wrote, 0
refused, 0 hand edits of Java.

## Assessment

Through the tools. The plan held; the layout fault was found by the golden written first.

## Follow-ups

- `TypeSwitch.switchCases` (the type form) still refuses a block in a switch expression; the enum
  form's `ownYield` would serve it.
- A one-line enum body (`enum Level { LOW, HIGH }`) in the method form has no golden: by reading,
  `EnumSwitch.inBody` indents each constant's body from the header's line, and the field form
  opens such a body up (`afterConstants`). Not checked by a run.
