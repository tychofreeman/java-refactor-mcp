---
order: 225
item: 233
status: Done
---

## Item

`batch` `retry`: a step number sent as a string (`"step": "2"`) was an engine error (a `ClassCastException`), and two changes of one step (two `step` 2, or `step` 2 and `remove` 2) were applied silently, one of them lost (issue #488, code review)

## Status

Done 2026-11-13 (`Batch.retried` refuses a number that is not a JSON number, and a step that `step` or `remove` names twice; `after` may repeat. `BatchRetryTest`. Default to reopen (the smallest): refuse a string over reading `"2"` as 2)

## Evidence

Issue #488; `docs/sessions/fix-batch-retry-numbers.md`
