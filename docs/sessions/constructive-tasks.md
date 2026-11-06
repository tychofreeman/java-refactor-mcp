# A task set for the constructive operations

- **Item:** the follow-up of `docs/sessions/ab-constructive-ops.md` (the user, 2026-11-06: "go
  ahead with the small fix and the new task set"): the shop tasks gave `implement`,
  `constructor`, `surround` and `test_for` little to do, so they went unused.
- **Date:** 2026-11-06
- **Branch:** constructive-tasks

## Plan

A fixture, `eval-tasks/testdata/library` (packages `lib.notify` and `lib.loan`), and four
tasks in `eval-tasks/library.json`, each one an IDE does with a constructive command:

| Task | What it asks | The operation that fits |
|---|---|---|
| library-sms | a new `Notifier` implementation, and a test of it | `create_file` `declaration` + `implement`; `test_for` |
| library-reservation | a class of three final fields with a constructor taking them | `constructor` |
| library-missing-file | `Library.load` returns an empty library for a missing file, other errors still thrown | `surround` (try, catching `NoSuchFileException`) |
| library-fines | fix `Fines.fine` (3 days' grace, 25 a day, at most 500) and add tests | `test_for` |

Each task's hidden test (`eval-tasks/testdata/library-<task>`) fails on the fixture and passes
with a reference solution (checked in scratch copies, 2026-11-06). The fines task's visible test
pins today's wrong fine, so a solution updates it, as shop-duplication's does `ExportTest`.

## Notes

- The fixture's Java and the hidden tests by `create_file` (outside this repo's source roots, so
  written as text); its Maven files copied from the shop fixture.
- Validation by a script over scratch copies: base 0; each hidden test 1 on the base, 0 with its
  reference.
