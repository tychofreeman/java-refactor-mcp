---
order: 525
item: 535
status: Open
---

## Item

`extract_variable` moves a call past a field read when the project code the call reaches writes no field of that kind: `calculator.keys += pending.pop().text`, where `pop()` writes only `Stack.held`.

## Status

Open (issue #700, filed 2026-11-20)

## Evidence

gorefactor's TypeScript issue #726, checked against Java on 2026-11-20: refused here as "calls code ... that could change keys"; with `keys` a local the same extraction applies.
