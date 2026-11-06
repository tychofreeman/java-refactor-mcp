# A/B check of the merged tool list

- **Item:** the A/B check gorefactor's `docs/parity.md` asks for before the merged names replace
  the old ones (one `move`, one `inline`, `extract_function`; roadmap #120-#122). The user asked
  for it on 2026-11-06, after the merges.
- **Date:** 2026-11-06
- **Branch:** ab-merged-tools

## What ran

The mcp arm only (the manual arm does not see the tool list, so its baselines stand), Sonnet,
the checkout at `main` after #242 (37 published tools), JDK 21 set:

- `eval-tasks/warehouse.json`, 4 runs of each of 4 tasks: `eval-runs/warehouse-sonnet-merged`.
- `eval-tasks/shop.json`, 2 runs of each of 4 tasks: `eval-runs/shop-sonnet-merged`.

Cost: $1.57 (warehouse) and $1.44 (shop), $3.01 in all.

## Results

| Run set | Runs | Passed | Cost per run | Turns per run |
|---|---|---|---|---|
| warehouse, baseline 1 (old list) | 16 | 16 | $0.122 | 12.4 |
| warehouse, baseline 2 | 16 | 16 | $0.119 | 13.4 |
| warehouse, baseline 3 | 16 | 15 | $0.095 | 9.8 |
| **warehouse, merged list** | 16 | **16** | **$0.098** | **9.8** |
| shop, baseline (old list) | 8 | 8 | $0.170 | 16.9 |
| **shop, merged list** | 8 | **8** | **$0.180** | **19.1** |

By task, merged against the latest baseline: warehouse-inline $0.081 (baseline 3 $0.077, the
earlier two $0.134 and $0.142), dedupe $0.122 (4/4; baseline 3 had its one failure there,
$0.112), rename $0.094 ($0.090), reorder $0.095 ($0.099); shop-duplication $0.240 ($0.220, 32.5
turns against 28), the other three within $0.013.

## What the models called

- `inline`: 9 calls and 3 batch steps in the warehouse runs, where baseline 3 called
  `inline_function` 6 times and in 6 steps; no run reached for an old name (no "No tool" refusal
  in any transcript).
- `move`: 3 calls, all in one shop-duplication run, all refused:
  1. without `file` (the model passed `from`, as gorefactor's `move` might be read): a schema
     error, resent with `file`;
  2. `move` of `Csv.exported` to `Exported` refused for an ambiguous `Order` (JUnit's or
     `shop.order.Order`), telling it to pass `imports`, and suggesting `org.junit.jupiter.api.Order`;
  3. resent with `imports`: "move does not take imports". The model then wrote the class itself.
  That is a bug of this repo's, not of the merge: `move_member` never took `imports` (its import
  fix passes none), and the refusal told it to pass them. Roadmap #131.
- `extract_function` once, `extract_struct` once; `create_file` 8 calls, 4 refused for the
  model's own compile errors.
- Errors in all: warehouse 11 (baseline 3: 10), shop 21 (baseline: 11); 10 of the shop ones are
  the three `move` calls, four `create_file` and two `insert_statements`.

## Assessment

- **The merged list does not cost the runs anything measurable:** every run passed; warehouse
  cost matches its latest baseline ($0.098 against $0.095) and is below the earlier two; shop is
  6% more, within the spread of two runs a task (shop-duplication alone accounts for most of it).
- **The models found the merged tools:** `inline` was used where `inline_function` had been, at
  the same or lower cost, and no call went to a removed name.
- **The one failure is a refusal that names an argument the tool lacks.** `move` (and
  `move_member` behind it) cannot take `imports`, so an ambiguous import there has no fix the
  model can send; and #105's suggestion put the test library's `Order` first over the project's
  own class. Both are in #131.

## Follow-ups

- Roadmap #131: `imports` on `move` (passed to every route that fixes imports), and an ambiguous
  name preferring a class of the project's own sources over a library's.
- More runs per task if a closer cost comparison on shop is wanted (2 per task is noisy).
