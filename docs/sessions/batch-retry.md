# batch retry: resend only the steps that change

- **Item:** the friction log of 2026-11-05 ("a batch refused for one name resent whole") and
  2026-11-06 (an `after` resend, 120 lines); first of the user's "do all 4" (2026-11-06). The
  user's choice: a retry with changed steps, over keeping the steps that passed (a half-done
  change left applied, no help for check "end") and over a better refusal only.
- **Date:** 2026-11-06
- **Branch:** batch-retry

## Plan

The server keeps the last refused batch (its steps and check, in `Session`). A batch with
`retry: true` sends only what changes: `{"step": N, ...}` replaces step N (its `tool` kept when
left out), `{"after": N, ...}` adds a step after step N (0 for first); the numbers are the refused
batch's. The batch runs whole, as any batch: nothing applies unless every step passes. A batch
that passes forgets the kept one. A refusal of a batch ends by saying how to retry.

Derivation: a unit test first (two calls: a refused batch, then a retry of its second step);
`Session` keeps the batch; `Batch.run` merges a retry (`Batch.retried`) and keeps or forgets;
the `Def`'s `retry` and description.

## Notes

- read: `Batch.run`, the `BATCH` `Def`'s schema (`tool` was required in each step, so a retry
  that keeps the tool would have failed the client's schema check; now optional, `Batch` still
  refuses a step without one), `Session`.
- The test first, red; one batch (the schema, the description, `Session`, `Batch.run` and
  `retried`); every test passed. #106's shift took one `old`.

## Measured

Through jrefactor: `create_file` of the test, one batch, the docs; no refusals; by hand this
record.

## Assessment

Through the tools, as planned. Whether models use `retry` is for a run to show: the refusal
names it each time.

## Follow-ups

- A step's args patched rather than replaced (a long `content` resent to fix one line of it).
