---
order: 41
item: 48
status: Done
---

## Item

`Gate.deferred` resets the flag to false in `finally`, not to its previous value, so a nested deferred call resets the outer one

## Status

Done

## Evidence

Full code review 2026-11-03. Fixed: a nested deferred call restores the outer flag (`GateTest.aNestedDeferredCallLeavesTheOuterOneDeferred`). Record: `docs/sessions/gate-deferred-nesting.md`
