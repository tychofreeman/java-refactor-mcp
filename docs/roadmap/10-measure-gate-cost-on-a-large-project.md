---
order: none
item: 10
status: Done
---

## Item

Measure gate cost on a large project; cache the reparse

## Status

Done

## Evidence

Commons Math (990 files): one edit 21.8 s to 2.1 s, `add_parameter` 15.0 s to 4.1 s, by one batch parse (`Jdt.parseAll`) and reusing the last count while no file changed. Opt-in `GateCostTest`; record in `docs/sessions/gate-cost.md`
