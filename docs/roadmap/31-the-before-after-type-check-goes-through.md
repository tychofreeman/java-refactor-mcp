---
order: 24
item: 31
status: Done
---

## Item

The before/after type check goes through `Gate`: `Batch` and `Stubs` each re-implement it

## Status

Done

## Evidence

Code review 2026-11-03. `Gate.checkedAtEnd` is the end-of-batch check; `Batch` calls it. `Stubs.declare` stays the declare loop. Record: `docs/sessions/gate-before-after.md`
