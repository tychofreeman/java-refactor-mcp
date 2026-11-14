---
order: 152
item: 159
status: Done
---

## Item

Generics, slice 3: `change_type` with `type_parameter`, a type parameter's bound added, replaced or removed on a class or a method's family (increment 7 of `docs/sessions/generics-sketch.md`)

## Status

Done 2026-11-13 (`TypeParameters.bound`; uses are not edited: the gate judges them, a method's calls keep their types, `CallTargets` keeps calls' targets and now also every override; a cast to the parameter whose erasure changes goes through `Exceptional.change`. The user, 2026-11-13, the sketch's recommendations: on `change_type`, over `change_signature` and a new tool; casts reported and refused under `preserve_exceptions`, over always refusing and over saying nothing)

## Evidence

A review found generics a gap; the friction log has no entry asking for it
