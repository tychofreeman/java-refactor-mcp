---
order: 85
item: 92
status: Done
---

## Item

`function_to_method` refuses when a subclass (or superclass) already has an instance method of the new signature: it would start to override (or be overridden by) it, and `x.area(3)` on a `Square` would run `Square.area(int)`

## Status

Done (2026-11-04), local session: `FunctionToMethod.refuseOverriding`; golden `function_to_method_refused_override`

## Evidence

Probe after `docs/sessions/stress-newer-tools.md`: applied and type-checked; 3 became 9. `FunctionToMethod`
