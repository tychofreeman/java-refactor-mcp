# A/B: do the refusals that name an operation save the steps?

- **Item:** the user's choice from "what's next?" (2026-11-07): rerun the two tasks whose runs
  spent extra steps on refusals that named no way out, now that #265 (blank finals name
  `constructor`), #267 (`create_file` over an existing file names `add_supertype`) and #268
  (must-override names `add_supertype`) are merged.
- **Date:** 2026-11-07
- **Branch:** ab-hints

## Plan

A measurement: the skeleton arm, Sonnet, 2 runs a task, on `eval-tasks/library-hints.json`
(`library-reservation` from `library.json`, `library-notifiers` from `library-repeat.json`), `main`
after #268, into `eval-runs/library-hints-skeleton`, about $0.55. Compare with the same tasks'
runs before (`ab-library-skeleton.md`: reservation $0.14 a run, 18.5 turns;
`repeated-constructions.md`: notifiers $0.139, 19.5 turns): turns, cost, and for each refusal
whether its hint was followed in the next call.

## Notes

- hand: the task file, by a script joining the two tasks from their sets.

## What ran

Sonnet, 2 runs a task, the skeleton arm only, `main` after #268, `eval-runs/library-hints-skeleton`.
Cost $0.52.

## Measured

| Task | Passed | Cost per run | Turns | Before (same arm) |
|---|---|---|---|---|
| reservation | 2/2 | $0.135 | 16.5 | $0.14, 18.5 turns (`ab-library-skeleton.md`) |
| notifiers | 2/2 | $0.125 | 15.0 | $0.139, 19.5 turns (`repeated-constructions.md`) |

What each hint did:

- **Blank finals name `constructor` (#265):** fired once (reservation run 1), and the next call was
  a batch with the fields and `add_declaration` with `constructor`. Before, both runs found the
  batch only after another refused attempt.
- **Must-override names `add_supertype` (#268):** fired once for all three classes (notifiers run
  2), and the next call was one batch of `add_supertype` and the methods for each. The same run
  before took `declaration` (refused, the file existed), then `overwrite` and fills.
- **`create_file` over an existing file names `add_supertype` (#267):** did not fire: notifiers run
  1 sent `declaration` with `overwrite` from the start, which replaces the stub file.
- **A way around:** reservation run 2 was refused `add_declaration` of a typed constructor (the
  skeleton refusal named `constructor`), then sent the whole class, constructor included, through
  `replace_declaration` of `Reservation`, which skeleton-only does not check.

## Assessment

- **The hints are followed when they fire:** both, in the next call, each saving the one or two
  refused attempts the earlier runs spent. Turns fell by 2 and 4.5 a run, cost by 4% and 10%;
  two runs a task, so the direction, not the size, is the finding.
- **The skeleton rule has a gap:** `replace_declaration` of a type takes typed members, so a
  model refused at `add_declaration` can write the same constructor there. Logged in
  `docs/friction-log.md`.
- **One change that would have helped:** the skeleton checks (`Generated`, and members typed into
  a type) on `replace_declaration` of a type too.

## Follow-ups

- Skeleton-only on `replace_declaration` of a whole type (friction log, 2026-11-07).
