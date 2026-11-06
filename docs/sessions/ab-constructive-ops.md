# A/B: the skeleton-only arm, again, with the constructive operations

- **Item:** the second of the user's "do all 4" (2026-11-06): rerun the shop tasks' skeleton-only
  arm now that the member-level operations exist (`add_declaration` `implement` and
  `constructor`, `replace_statements` `surround`, #123) and `batch` `retry` (#247).
- **Date:** 2026-11-06
- **Branch:** ab-constructive-ops

## What ran

`eval-tasks/shop.json`, the mcp arm with `eval-tasks/jrefactor-ab-server-skeleton`
(`JREFACTOR_SKELETON_ONLY=1`), Sonnet, 2 runs a task, `main` after #249:
`eval-runs/shop-sonnet-skeleton-2`. Cost $1.49. Run last, after the day's server changes, as the
harness rebuilds its jar from this checkout.

## Results

| Run set | Passed | Cost per run | Turns | Typed lines | Errors |
|---|---|---|---|---|---|
| skeleton, 2026-11-05 (before #123) | 8/8 | $0.217 | 25.8 | 791 | 34 |
| **skeleton, now** | **8/8** | **$0.186** | **22.1** | **564** | **18** |
| mcp without the flag (merged list, 2026-11-06) | 8/8 | $0.180 | 19.1 | | 21 |

By task, now against before: address $0.200 ($0.230), currency $0.199 ($0.279), duplication
$0.239 ($0.256), tangled $0.105 (the same).

## What the models called

- `create_file` with `declaration` 3 times (5 before); `test_for` 0; `implement`, `constructor`
  and `surround` 0. The new constructive options were not used.
- `batch` `retry` twice, in one address run: the first refused (a new step numbered 4 in a batch
  of 3, "the refused batch has steps 1 to 3; 4 is none of them"), the second at once with
  `after`, applied (6 steps), with about 1,500 characters sent instead of the batch again.
- More refactorings than before: `extract_function` 1, `fold` 2, `move` 1, `extract_struct` 2;
  fewer `replace_declaration` steps (10 against 23).

## Assessment

- **Cheaper and less typed, but not by the constructive options.** Cost fell 14% and typed lines
  29%, errors by half; the skeleton arm now costs about what the arm without the flag does. None
  of `implement`, `constructor`, `surround` or `test_for` was called, so the gain comes from the
  rest of the day's work (refusals that say where, imports that resolve, `retry`, the merged
  names), not from the member-level operations. The thesis is still not tested by these tasks:
  the shop features add methods and fields to existing classes more than they make new types
  from supertypes, so the new options have little to do.
- **`retry` was found from the refusal text and used.** Its one refusal suggests taking
  `{"step": N}` one past the end as an append.
- Two runs a task: the per-task differences are within noise; the totals are the signal.

## Follow-ups

- `retry`: accept a `step` one past the end as an added last step.
- A task set that asks for new implementations of an interface, a constructor, a test from a
  target and error handling around a call, to measure the constructive options (it would cost
  another run; ask).
