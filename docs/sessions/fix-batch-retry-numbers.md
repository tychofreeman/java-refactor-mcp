# batch retry: refuse a step number that is no number, or changed twice

- **Item:** issue #488 (code review of 2026-11-13), roadmap #233
- **Date:** 2026-11-13
- **Branch:** fix-batch-retry-numbers

## Plan

In `Batch.retried`, check the number's type before the cast (refuse naming the form) and keep the
set of numbers `step` and `remove` have changed, refusing the second. `after` may repeat: two
steps added after one step is a use, not a conflict. Two unit tests in `BatchRetryTest` first.

## Notes

- plan: tests `aStepNumberThatIsNoNumberIsRefused` (red: ClassCastException) and
  `aStepChangedTwiceIsRefused` (red: no refusal) first, added in one `batch`.
- plan: a new step one past the last (`step` N+1) sent twice is refused too, as a number named
  twice; `after` the last adds several.

## Measured

Not measured: the session-log hook is not loaded here. Three batches or calls; no Java by hand.

## Assessment

Through the tools.

## Follow-ups

None.
