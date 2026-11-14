# type_switch_to_method over an enum takes a bare throw arm

- **Item:** issue #435, from the Java 17 stress run (`docs/sessions/stress-enum-switch.md`):
  `default -> throw ...;` was refused, the block `{ throw ...; }` applied. Roadmap #182.
- **Date:** 2026-11-13
- **Branch:** fix-enum-switch-throw

## Plan

`EnumSwitch.arms` reads the node after each arrow as an expression (a `yield`'s, or an expression
statement's in a switch statement) or a block, else refuses. A `ThrowStatement` there is a
statement the method body can hold as it is: read it as a body, as a block is. `TypeSwitch.body`
writes a statement's text unchanged (neither an expression nor a block), and `constant` is false
for it, so the field form falls back to the method form, or refuses under `as=field`. One
`replace_text` of the conditional; golden `type_switch_enum_method_throw` first.

## Notes

- read: `inspect` of `TypeSwitch` (outline, `run`, `switchCases`, `usesOnly`, `asReceiver`) and
  `EnumSwitch` whole (505 lines; the whole file, since the fix's place and both forms' handling of
  the body were in it).
- plan: golden written first, failed with the issue's refusal.
- `replace_text` of the conditional's last branch (imports `Statement`, `ThrowStatement` added,
  `Statement` chosen over `java.beans` and `java.sql` by the package's other files), and of the doc
  comment. Spotless re-wrapped the line.
- Golden passed with the expected output and result on the first run.

## Measured

Not measured: the session-log hook is not loaded in this session. Two tool edits, no hand edits
of Java.

## Assessment

Through the tools. The plan held. `TypeSwitch.switchCases` (the type-pattern form) has the same
gap, and also refuses a block in a switch expression; not changed, as no stress call or task met
it.

## Follow-ups

- The type-pattern form's bare `throw` and block arms in a switch expression, if a task meets them.
