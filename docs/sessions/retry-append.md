# batch retry: a step one past the end is added last

- **Item:** the follow-up of `docs/sessions/ab-constructive-ops.md` (the user, 2026-11-06): a model
  numbered a new step 4 in a refused batch of 3 and was refused.
- **Date:** 2026-11-06
- **Branch:** retry-append

## Plan

In `Batch.retried`, `{"step": N}` with N one past the refused batch's last step adds it at the
end (as `{"after": N-1}`), its `tool` required then. A test first, in `BatchRetryTest`.

## Notes

- The test red, one `replace_statements` in `retried`, green; the refusal now names the number
  that appends.

## Measured

Through jrefactor: one `add_declaration` (the test), one `replace_statements`; the contract by
script; by hand this record.

## Assessment

Through the tools, as planned.

## Follow-ups

None.
