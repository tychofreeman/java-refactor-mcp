# batch: refuse undo and batch as steps under a client's name

- **Item:** issue #473 (code review of 2026-11-13), roadmap #231
- **Date:** 2026-11-13
- **Branch:** fix-batch-client-name

## Plan

Move the check in `Batch.steps` after `Tools.named` and compare `def.name()`, which has the
client's `mcp__<server>__` prefix removed. A golden refusal case first.

## Notes

- plan: golden `batch_refused_client_undo` first; red: the step ran `undo` ("Nothing to undo in
  this session") instead of being refused.

## Measured

Not measured: the session-log hook is not loaded here. One `replace_text`; no Java by hand.

## Assessment

Through the tools.

## Follow-ups

None.
