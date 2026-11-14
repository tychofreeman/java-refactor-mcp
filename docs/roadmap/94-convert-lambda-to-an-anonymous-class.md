---
order: 87
item: 94
status: Done
---

## Item

`convert_lambda` to an anonymous class refuses a bare call of a method the interface inherits (a default of a superinterface), not only of those it declares

## Status

Done (2026-11-04), local session: `ConvertLambda.refuseSelf` walks the supertypes; golden `convert_lambda_to_anonymous_refused_inherited`

## Evidence

Probe after `docs/sessions/stress-newer-tools.md`: `() -> label()` became an anonymous class whose `label()` is the inherited default. `ConvertLambda.refuseSelf`
