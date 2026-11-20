---
order: 525
item: 535
status: Done
---

## Item

`extract_variable` moves a call past a field read when the project code the call reaches writes no field of that kind: `calculator.keys += pending.pop().text`, where `pop()` writes only `Stack.held`.

## Status

Done (2026-11-20, issue #700): `FieldWrites`, asked by `Behavior.checkCrossed`; the owner chose to follow project calls (`docs/design.md`, Decisions).

## Evidence

gorefactor's TypeScript issue #726, checked against Java on 2026-11-20: refused here as "calls code ... that could change keys"; with `keys` a local the same extraction applies.
