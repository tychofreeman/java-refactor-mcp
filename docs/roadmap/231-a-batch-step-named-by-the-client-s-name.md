---
order: 223
item: 231
status: Done
---

## Item

A `batch` step named by the client's name (`mcp__jrefactor__undo`, `mcp__jrefactor__batch`) passed the check that refuses `batch` and `undo` as steps, which compared the raw name, and ran inside the batch's journal entry (issue #473, code review)

## Status

Done 2026-11-13 (`Batch.steps` checks the resolved `def.name()`; golden `batch_refused_client_undo`)

## Evidence

Issue #473; `docs/sessions/fix-batch-client-name.md`
