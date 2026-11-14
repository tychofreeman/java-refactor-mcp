---
order: 258
item: 268
status: Done
---

## Item

The method a call node runs was enumerated five times (`MethodFamily`, `ByLine`, `ThrowsCallers`, `CallTargets`, `TypeParameters`), and only `MethodFamily` applied `Signatures.constructed`: `add_parameter` by the line of `new Base(1) {...}` was refused naming `Use..(int)`, and `change_signature` `add_throws` on `Base(int)` did not reach that construction's caller, refused as an unhandled exception (issue #503, code review)

## Status

Done 2026-11-14: `Calls.binding(node)` (a call, a super call, a construction or an enum constant through `Signatures.constructed`, `this(...)`, `super(...)`, a method reference; was `CallTargets.binding`); `ThrowsCallers`, `ByLine`'s constructions, `MethodFamily` and `TypeParameters` (its `callBinding` inlined) call it. Goldens `add_parameter_by_line_anonymous`, `change_signature_propagate_anonymous` (both failed before), `change_type_parameter_anonymous` (a pin). Left: `ByLine` has no enum-constant hit, and `SafeDelete` and `Signatures.addComponent` still compare the raw binding

## Evidence

Issue #503; `docs/sessions/call-binding.md`
