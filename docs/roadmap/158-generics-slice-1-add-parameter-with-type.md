---
order: 151
item: 158
status: Done
---

## Item

Generics, slice 1: `add_parameter` with `type_parameter` and `name` a method: the method's family declares the type parameter and `value` becomes it in their parameter and return types (increment 6 of `docs/sessions/generics-sketch.md`)

## Status

Done 2026-11-13 (`TypeParameters.toMethod`, on `MethodFamily` with method references kept: calls are not edited, each must keep its parameter and return types, inferring `value`, and its target (`CallTargets`); refused: a family a lambda implements, `value` in the return type only, a constructor, a method of a record or an enum. The user, 2026-11-13, the sketch's recommendation: on `add_parameter`; a call that would infer another type is refused, writing `<String>` there left to issue #375)

## Evidence

A review found generics a gap; the friction log has no entry asking for it
