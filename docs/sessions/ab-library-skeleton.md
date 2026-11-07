# A/B: the skeleton arm on the library tasks, with the widened refusals

- **Item:** the user's choice from "what's next?" (2026-11-06), the follow-up of
  `skeleton-generated-bodies.md` (#255): whether the refusals of typed bodies a command writes
  steer models to `implement`, `constructor` and `surround`, which no run has used yet.
- **Date:** 2026-11-06
- **Branch:** ab-library-skeleton

## Plan

A measurement, no code: the skeleton arm alone (`-arms mcp`, `jrefactor-ab-server-skeleton`),
Sonnet, 2 runs a task, on `eval-tasks/library.json`, `main` after #263, into
`eval-runs/library-sonnet-skeleton-2`. Compare with the same arm before #255
(`ab-library.md`: 7/8, $0.117 a run, 396 typed lines, no constructive option used), counting
the calls of each constructive option and the refusals that name one. `src/main` stays
unchanged while it runs.

## Notes

- plan: typed lines counted the same way for both runs (lines of `content`, `new` and
  `declaration` arguments, batch steps included), since the earlier record does not say how.

## What ran

Sonnet, 2 runs a task, the skeleton arm only, `main` after #263, `eval-runs/library-sonnet-skeleton-2`.
Cost $1.01. The tools appear as `mcp__gorefactor__*` there.

## Measured

| Run set | Passed | Cost per run | Turns | Typed lines | Tool errors |
|---|---|---|---|---|---|
| skeleton, before #255 (`ab-library.md`) | 7/8 | $0.117 | 13.8 | 409 | 19 |
| **skeleton, now** | **8/8** | **$0.126** | **16.5** | **425** | **18** |

By task, cost per run: sms $0.12, reservation $0.14, missing-file $0.11, fines $0.14.

What the models called, by run:

| Task | Constructive calls | After a refusal that named it |
|---|---|---|
| reservation (both runs) | `constructor` | yes: the typed constructor was refused, naming `constructor: ["book", "member", "due"]` |
| missing-file, run 2 | `surround: "try"` | yes: the typed try was refused, naming `surround` |
| missing-file, run 1 | none | no refusal due: the declaration moved out of the try, so it was not a wrap of unchanged statements |
| sms (both runs) | `declaration` with `implements`, `test_for` | yes for `declaration` (a whole file refused); `test_for` followed the same refusal |
| fines (both runs) | `test_for` | no: unprompted, the first call of each run |

`implement` was not called, and no task needed it: `create_file` with `declaration ... implements
Notifier` already declared the interface's methods as stubs, which the runs then filled.
No session record or code was written through jrefactor on this branch: a measurement, by hand
only this record and the docs.

## Assessment

- **The refusals steer; descriptions did not.** Every refusal that named an operation was followed
  by that operation, in the next call or the next batch: `constructor` twice, `surround` once,
  `declaration` four times. Before #255 none of these options was called in the same tasks. This
  is the first measurement where the constructive member operations carry the work, and it
  matches gorefactor's lesson: a mechanism changes behavior, guidance does not.
- **`test_for` is found unprompted** (the fines runs' first call); it is the one option whose name
  says what the model was about to do.
- **The steering costs a turn.** Cost per run is 8% higher and turns 20% more: each steer is a
  typed attempt, a refusal, and the operation. Typed lines did not fall (425 against 409): the
  operations replace short bodies (a three-line constructor, a try), and the model still types its
  first attempt before the refusal. On tasks this small the saving cannot show; the claim to test
  next is whether the model starts with the operation once it has been refused once.
- **All 8 passed** (7 before): the earlier failure was a heredoc the write guard refused, not
  something #255 changed.
- **One change that would have helped:** in the reservation runs, the fields were added before the
  constructor, so the `final` fields failed to compile ("may not have been initialized") and the
  model needed a batch. A compile refusal for uninitialized `final` fields could name
  `constructor` (with the field names), as the skeleton refusal does. Logged in
  `docs/friction-log.md`.

## Follow-ups

- The compile refusal for blank `final` fields naming `constructor` (friction log, 2026-11-06).
- Whether a model refused once starts with the operation on the next file: a longer task set with
  repeated constructions would show it.
