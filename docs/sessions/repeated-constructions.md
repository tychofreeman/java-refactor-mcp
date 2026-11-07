# Repeated constructions: does a model refused once start with the operation?

- **Item:** the user's choice from "what's next?" (2026-11-06), the open question of
  `ab-library-skeleton.md` (#264): every refusal that named `constructor`, `surround` or
  `declaration` was followed by it, a turn each; whether the next construction of the same kind
  starts with the operation was not measured, as each task had one.
- **Date:** 2026-11-06
- **Branch:** repeated-constructions

## Plan

Two tasks on the library fixture, each asking for three constructions of one kind, then the
skeleton arm on them (Sonnet, 2 runs a task, about $0.60, the user's yes given by the choice):

- `library-value-classes`: three classes in `lib.loan` with final fields, getters named as the
  fields and a constructor taking them in order (`Reservation`, `Renewal`, `Hold`): `constructor`
  three times.
- `library-notifiers`: three `Notifier`s in `lib.notify` (`SmsNotifier`, `PushNotifier`,
  `PagerNotifier`): `declaration ... implements Notifier` three times.

The hidden tests (`eval-tasks/testdata/library-<task>/.../AcceptTest.java`) by `create_file`,
validated as #252's were: in scratch copies, each fails on the fixture and passes with a
reference solution. The measure, per run: for each construction, whether it began with the
operation or with a typed attempt that was refused, and whether the first refusal changed the
later ones. `src/main` stays unchanged during the run.

## Notes

- hand: the task file (`eval-tasks/library-repeat.json`, JSON) by a heredoc; the reference
  solutions by a Python script in the scratch copies only, never in the repo.
- read: the reservation and sms hidden tests and `Notifier`, to model the new tests on them.

## What ran

Sonnet, 2 runs a task, the skeleton arm only, `main` after #265, `eval-runs/library-repeat-skeleton`.
Cost $0.49. Validation: in scratch copies each hidden test failed on the fixture (exit 1) and
passed with a reference solution (exit 0).

## Measured

| Task | Passed | Cost per run | Turns |
|---|---|---|---|
| value-classes | 2/2 | $0.106 | 13 |
| notifiers | 2/2 | $0.139 | 19.5 |

`bin/session-report` for the branch: 18 tool calls; through jrefactor 2 edit steps (the hidden
tests, by `create_file` through the CLI), 0 refusals; by hand 4 (this record, the task JSON, the
scratch validation, the run); large writes 0; reads: inspect 3.

What the models did:

- **value-classes, both runs:** each class as a header only, `public record Reservation(Book book,
  Member member, LocalDate due) {}`, which skeleton-only allows (no members) and which gives the
  final fields, accessors and constructor the task asked for. `constructor` was never needed.
  The test file was refused once (typed whole), then started with `declaration`.
- **notifiers, both runs:** the test was written first, so the edit declared `SmsNotifier`,
  `PushNotifier` and `PagerNotifier` as stubs: classes with no supertype and package-private
  `channel()` and `format()`. Then the three constructions went in one turn, in parallel:
  - run 1: three `create_file` with whole bodies, all refused at once; then one batch of
    `add_supertype implements Notifier` and `replace_declaration` of both methods, for all three.
  - run 2: three `create_file` with `declaration ... implements Notifier`, started that way after
    the test file's refusal named `declaration`, but refused because the stubs' files exist; then
    `add_declaration` of the methods, refused ("must override or implement a supertype method");
    then `declaration` with `overwrite: true`, whose stubs it filled with `replace_statements`.

## Assessment

- **The question cannot be answered on these tasks the way it was asked.** A model does three
  like constructions in one turn, in parallel, so one refusal reaches all three at once, and the
  next turn changes all three. What can be seen is the carry-over within a run: in notifiers run
  2 the refusal of a typed test file made the model start the three classes with `declaration`,
  a construction of another kind. In value-classes both runs did the same for the test.
- **A higher-level construct beat the operation:** a record header is less typing than a class
  plus `constructor`, and the tool allowed it. The task let it; one that needs a mutable field
  or a class would test `constructor`.
- **Test-first stubs get in the way of `declaration`.** The stubs from a test are a class with no
  supertype; `create_file` with `declaration` then refuses for an existing file, and adding the
  methods refuses for `@Override`. The models found `add_supertype` (run 1) or `overwrite` (run 2),
  three to six extra steps each. Logged in `docs/friction-log.md`.
- **Through the tools:** the hidden tests through `create_file`; the rest is data and a run.
- **One change that would have helped:** `create_file` with `declaration` replacing a file that
  holds only stubs this session declared, or its refusal naming `add_supertype`; see the friction
  log.

## Follow-ups

- `declaration` over a file of the session's own stubs (friction log, 2026-11-06).
- A task that needs `constructor` (a class with a mutable field, or "not a record"), done one at a
  time by asking for the second class only after the first is used, if the per-construction
  question is still wanted.
