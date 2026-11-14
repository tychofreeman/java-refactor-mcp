---
order: 207
item: 215
status: Done
---

## Item

`type_switch_to_method` on an `if` chain ignored case order: a case of a subtype after its supertype's case never ran, and as an override it ran instead

## Status

Done 2026-11-13 (`TypeSwitch.chain` refuses a case whose type `isSubTypeCompatible` with an earlier case's, both erasures from the one parse; probe `type_switch_chain_order`, golden `type_switch_to_method_refused_order`). Default to reopen (the smallest fix): refuse, over dropping the unreachable case and over reordering it

## Evidence

Issue #467 (code review of 2026-11-13); `docs/sessions/fix-type-switch-order.md`
