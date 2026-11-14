---
order: 135
item: 142
status: Done
---

## Item

`add_parameter` and `remove_parameter` did not reach an enum constant with a body (refused by the gate, "The constructor Color() is undefined"), and the refusal for an enum with no constructor named a fix that was itself refused (the enum sketch, increment 2)

## Status

Done 2026-11-13 (`Signatures.constructed`: the constructor an anonymous class's arguments go to, asked by `MethodFamily` for an enum constant and for `new T(...) {...}`, which had the same fault; the refusal names `add_declaration` with content `Color() {}` for an enum, which #141 made work, the smaller of the two: `constructor` takes fields, and a constructor with parameters would not compile until each constant passes them)

## Evidence

`docs/sessions/enums-sketch.md`, section 7; `docs/sessions/enum-constant-body-parameter.md`
