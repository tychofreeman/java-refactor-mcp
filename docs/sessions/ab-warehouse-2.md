# A/B rerun after #99-#101

- **Item:** the rerun of `docs/sessions/ab-warehouse.md`'s task set after the three
  mechanisms it found were fixed (#99 #174, #100 #177, #101 #178). The user's
  request of 2026-11-05.
- **Date:** 2026-11-05
- **Branch:** ab-warehouse-2

## Method

As the first run: gorefactor's `cmd/refactor-ab` (its `main` at 033e165, with
Maven in the mcp arm's Bash), this repo's `main` at dc15dea through
`eval-tasks/jrefactor-ab-server`, Sonnet, `-arms mcp,manual -runs 4`, the same
tasks and per-arm instructions. Output in `eval-runs/warehouse-sonnet-2` (not
committed).

## Results (32 of 32 passed, $3.44)

| task | arm | first run | rerun | turns (rerun) |
|---|---|---|---|---|
| reorder | mcp | $0.11 | $0.09 | 10.5 |
| reorder | manual | $0.11 | $0.11 | 11.5 |
| inline | mcp | $0.13 | $0.14 | 17.8 |
| inline | manual | $0.09 | $0.09 | 10.5 |
| rename | mcp | $0.09 | $0.09 | 11.2 |
| rename | manual | $0.09 | $0.09 | 10.0 |
| dedupe | mcp | $0.16 | $0.15 | 14.2 |
| dedupe | manual | $0.08 | $0.09 | 6.2 |

- Per run: mcp $0.122 to $0.119; manual $0.093 to $0.096. The tools cost 24% more,
  from 31%. Run-to-run noise in gorefactor's runs is about 10%, so this is the
  direction, not a measurement of its size.
- **Reorder, where #99 hit:** the mcp arm is now the cheaper. Each run extracted
  the first `p.bin()` (#99), gave a wrong column once, and took the column the
  refusal named (#101) on the next call.
- **#100:** no step named by its client name was refused.

## What the mcp arm still spends on

- **Inline (20 refusals in 4 runs):** `inline_function` refused each helper, as
  it should (the arguments reorder, short-circuit and duplicate). The refusal's
  advice, "put one of them in a variable first", left the order to the model:
  extracting `charge(truck)` before `reserve(truck)` was refused as a reorder,
  and batches gave line numbers shifted by their own earlier steps. A standard
  inline binds such arguments to locals itself; roadmap #102.
- **Dedupe (no refusals):** reading (14 `inspect`s) and composing ten
  replacements by hand. gorefactor's derivation for this task ends in `fold`,
  which Java does not have; roadmap #103.
- ToolSearch is still 7-8 calls per task's four runs: the per-arm instructions
  do not say to load every schema at once.

## Follow-ups

- #102, #103; then rerun. Say in `eval-tasks/claude-md/mcp.md` to load all
  jrefactor schemas with one ToolSearch (instruction, not mechanism; measure it).
