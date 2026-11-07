# A/B: value-classes after the duplicate-constructor fix

- **Item:** the user's request (2026-11-07), the follow-up of `ab-constructors.md` (#274) after
  #275 (a constructor replaces one that only assigns fields) and #276 (skeleton-only checks
  `replace_statements` in a member's body).
- **Date:** 2026-11-07
- **Branch:** ab-value-classes-rerun

## Plan

A measurement, no code: the skeleton arm, Sonnet, 2 runs, `main` after #276, on
`eval-tasks/library-value-classes.json` (the value-classes task alone), into
`eval-runs/value-classes-rerun`. Per run: whether the fields and `constructor` were sent together,
and whether any "Duplicate method" refusal came. Compare with #274 ($0.103 and $0.263; 13 and 23
turns) and #266 ($0.106, 13 turns a run). A run that writes records does not test the fix.

## Notes

- hand: the task file, by a Python script taking the task from `library-repeat.json`.

## What ran

Sonnet, 2 runs, the skeleton arm only, `main` after #276, `eval-runs/value-classes-rerun`. Cost
$0.22.

## Measured

| Run | Passed | Cost | Turns | What it wrote |
|---|---|---|---|---|
| 1 | yes | $0.124 | 11 | three classes: fields, then `constructor`, in one batch |
| 2 | yes | $0.097 | 11 | three record headers (does not test the fix) |
| #274, run 2 | yes | $0.263 | 23 | the same plan as run 1, refused three times for duplicates |
| #266 | yes | $0.106 | 13 a run | record headers |

Run 1: the first batch sent the fields with typed constructors, refused by skeleton-only (naming
`constructor`); the `retry` sent the fields and getters, then `add_declaration` with `constructor`
for each class. Each fields step wrote the constructor (#272, with the record hint), and each
`constructor` step replaced it: "replaced the constructor Hold(Member, Book, int), which only set
fields". No "Duplicate method" refusal in either run.

## Assessment

- **The duplicate is gone:** the plan that cost #274's run 2 ten turns now passes in one retry;
  the run took 11 turns, fewer than either earlier set.
- **The `constructor` step is now redundant but harmless:** the fields step had written the same
  constructor. Run 1 passed `before: "book"`, but a replacement keeps the replaced member's place, so
  the constructor stayed after the getters, where #272 had put it after the last field.
- **The record hint was not followed** in run 1, though the task allows records; run 2 wrote
  records without it.
- **Through the tools:** a measurement; the task file by a script.

## Follow-ups

- A replacement could honor `after`/`before` when they are given.
