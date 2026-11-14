---
order: 71
item: 78
status: Done
---

## Item

`inline_function` accepts an argument whose type is a subtype of the parameter's (a `String` for an `Object`), refused today by the conversion rule; numeric and boxing conversions stay refused

## Status

Done (2026-11-04), local session: a subtype when every use is a call receiver; record in `docs/sessions/inline-function-subtype.md`

## Evidence

Follow-up of `docs/sessions/effect-order.md`: the rule is broader than the probe (`half(3)` for `double`) needed. Needs a golden showing the body behaves the same
