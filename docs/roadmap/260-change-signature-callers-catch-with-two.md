---
order: 250
item: 260
status: Done
---

## Item

`change_signature` `callers=catch` (or a `stop_at`) with two calls of the method in consecutive statements: the first try's `} catch` and the second's `try {` went in at one offset, in the wrong order, and gave an empty try that did not compile, refused as the model's to fix (issue #478, code review)

## Status

Done 2026-11-13 (`ThrowsCallers.write` sorts the tries by start and merges those around adjacent statements of one block, as it merged overlapping ones; golden `change_signature_catch_adjacent`)

## Evidence

Issue #478; `docs/sessions/fix-catch-consecutive-calls.md`
