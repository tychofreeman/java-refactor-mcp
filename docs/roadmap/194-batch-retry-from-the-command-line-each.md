---
order: 187
item: 194
status: Done
---

## Item

`batch` `retry` from the command line: each `bin/jrefactor` run is a new process, so the refused batch a refusal offered to retry was gone in the next run

## Status

Done (2026-11-13): `Batch.save` and `Batch.load` keep it in the cache directory beside the undo stack (`CliTest.aRefusedBatchIsRetriedByALaterRun`); record in `docs/sessions/cli-batch-retry.md`

## Evidence

Friction log: move-recursive-static and skeleton-build-blocks (2026-11-13), resent whole each time
