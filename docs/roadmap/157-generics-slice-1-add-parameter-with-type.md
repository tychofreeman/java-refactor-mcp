---
order: 150
item: 157
status: Done
---

## Item

Generics, slice 1: `add_parameter` with `type_parameter` and `members`: the named fields, methods and constructors are typed `T` where their declared types name `value` (increment 5 of `docs/sessions/generics-sketch.md`)

## Status

Done 2026-11-13 (`TypeParameters.typed`: signatures only, bodies left to the gate, whose fix line names a bound; refused up front: a static member, a field created as an array of `value`, a field declared with others, a method that overrides a supertype's, a member that does not name `value`. `CallTargets` now passes a virtual call that names a method its old target overrides. The user, 2026-11-13, the sketch's recommendation: signatures only, over retyping the locals the bodies initialize and over inferring the rest from one member)

## Evidence

A review found generics a gap; the friction log has no entry asking for it
