# A/B: do the written constructors save the turns?

- **Item:** the user's request (2026-11-07), after #272 (an edit that leaves final fields unset
  writes their constructor, with a record hint) and #273 (a constructor stub sets the fields its
  arguments fit).
- **Date:** 2026-11-07
- **Branch:** ab-constructors

## Plan

A measurement, no code: the skeleton arm (`-arms mcp`, `jrefactor-ab-server-skeleton`), Sonnet,
2 runs a task, `main` after #273, on `eval-tasks/library-constructors.json`: library-reservation
(from `library-hints.json`) and library-value-classes (from `library-repeat.json`), into
`eval-runs/library-constructors-skeleton`. Compare with reservation in `ab-hints.md` ($0.135,
16.5 turns) and value-classes in `repeated-constructions.md` ($0.106, 13 turns). Per run: whether
a constructor was written by #272 or #273, typed and refused, or sent as `constructor`, and
whether the record hint was followed. `src/main` stays unchanged during the run.

## Notes

- hand: the task file, by a Python script joining the two existing tasks.

## What ran

Sonnet, 2 runs a task, the skeleton arm only, `main` after #273,
`eval-runs/library-constructors-skeleton`. Cost $0.63. The tools appear as `mcp__gorefactor__*`.

## Measured

| Task | Run | Passed | Cost | Turns | Before |
|---|---|---|---|---|---|
| reservation | 1 | yes | $0.136 | 19 | $0.135, 16.5 turns a run (`ab-hints.md`) |
| reservation | 2 | yes | $0.127 | 17 | |
| value-classes | 1 | yes | $0.103 | 13 | $0.106, 13 turns a run (`repeated-constructions.md`) |
| value-classes | 2 | yes | $0.263 | 23 | |

What the models did:

- **reservation, both runs:** the typed constructor was refused under skeleton-only (naming
  `constructor`); the model then sent the fields without it (run 2 with the getters), and #272
  wrote the constructor in that call ("added constructor Reservation(Book book, Member member,
  LocalDate due), since final fields ... were unset"). No `constructor` call was needed. The record
  hint was in both results and not followed: the task asks for a class with getters.
- **value-classes, run 1:** three record headers by `create_file`, as both runs of #266; no
  constructor at all.
- **value-classes, run 2: #272 and `constructor` collided.** The model planned each class as the
  tools describe: `declaration`, then the final fields, then `add_declaration` with `constructor`,
  then the getters, in one batch. The fields step had already written the constructor, so the
  `constructor` step added a second: "Duplicate method Hold(Member, Book, int)", for all three
  classes. Three batches were refused over it (one `retry`, one rewritten, one by
  `replace_declaration` of the whole class, the last refused first by #271's check), and the model
  got through by `replace_text` of each test stub's header and `replace_statements` of its body
  with the assignments typed: the stubs from the test, which #273 left throwing as the classes had
  no fields then. 10 extra turns, $0.16.
- **#273 did not fire:** in every run the classes had no fields when the test declared their
  constructors.

## Assessment

- **#272 saves the `constructor` call when the model leaves it out**, as both reservation runs did
  after a refusal: the turn the hint used to cost went into the refusal of the typed constructor,
  so cost and turns are as before.
- **#272 breaks the plan the tools taught.** The `constructor` option, the blank-final hint (#265)
  and `docs/tool-contract.md` all say: send the fields and `constructor` together. A model doing
  exactly that now gets a duplicate. This is a regression of #272, and the cost of the one run that
  hit it outweighs what the others saved.
- **Fix:** `add_declaration` of a constructor (by `constructor` or content) whose parameter types
  are those of a constructor that only assigns parameters to fields should replace it, as a stub is
  replaced, and say so. Then fields-then-`constructor` works, and so does a typed constructor sent
  after the fields outside skeleton-only. Logged in `docs/friction-log.md`.
- **`replace_statements` of a stub body passes skeleton-only:** the run typed three field-assigning
  constructors that way, after every other route was refused.
- **Through the tools:** a measurement; the task file by a script.

## Follow-ups

- Replace an assigning constructor of the same parameter types (above).
- Skeleton-only on `replace_statements` that turns a stub constructor into field assignments.
- Rerun value-classes after the fix; #273 needs a task where the class has fields before the test.
