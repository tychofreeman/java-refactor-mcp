---
order: 13
item: 11
status: Done
---

## Item

A/B harness run on a small Java task set against a stock baseline

## Status

Done (2026-11-05), local session: gorefactor's `cmd/refactor-ab` on a Java port of its warehouse traps (`eval-tasks/warehouse.json`), Sonnet, 4 runs per arm and task: every run passed in both arms (one manual run failed to start), mcp $0.12 a run against manual $0.09 (+32%; gorefactor's same set, +20%); record in `docs/sessions/ab-warehouse.md`

## Evidence

Needs edits, batch and stubs to be a fair test; gorefactor's fresh-model arm cost about twice the manual arm until they existed. Rerun after #99-#101 (2026-11-05, `docs/sessions/ab-warehouse-2.md`): 32/32 passed, mcp $0.119 against manual $0.096 a run (+24%, from +31%); reorder now cheaper with the tools; the rest is #102 and #103. Third run after them (`docs/sessions/ab-warehouse-3.md`): mcp $0.095 against manual $0.093 a run (+2%); inline and reorder cheaper with the tools; one dedupe run folded only the shared statement and failed the task's structure test
