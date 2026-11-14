---
order: 86
item: 93
status: Done
---

## Item

`inline_function` qualifies a bare member of the body (`count`) that a local or parameter of the calling member hides (`this.count`, or the type for a static)

## Status

Done (2026-11-04), local session: `InlineFunction.shadowEdits`; golden `inline_function_shadowed_field`

## Evidence

Probe after `docs/sessions/stress-newer-tools.md`: `total()` returning `count * 2` inlined into a method with a local `count` read the local. `InlineFunction`
