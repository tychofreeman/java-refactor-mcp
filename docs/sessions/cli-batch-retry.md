# A refused batch kept between command-line runs

- **Item:** `batch` `retry` from `bin/jrefactor`: a refusal offers `retry`, and the next run
  refuses it, as each run is a new process. Friction log, three entries (move-recursive-static,
  skeleton-build-blocks, and before); the first of four items the user chose in order on
  2026-11-13.
- **Date:** 2026-11-13
- **Branch:** cli-batch-retry

## Plan

The command line keeps a session's state on disk between runs (the undo stack, the last type
count); the refused batch is session state it does not keep. Keep it beside them.

1. Red: a `CliTest` case, a batch refused in one run and fixed by `retry` in the next.
2. Green: `Batch.save(project, file)` and `Batch.load(project, file)` (the refused steps and
   their check, or no file), as `Journal` and `TypeCheck` have; `Cli.run` loads it with them and
   saves it after the call, refused or not.

## Notes

- read: `Cli.run`, `Journal.save`/`load`, `Batch.run`, `TypeCheck.load`: where the command line
  keeps session state between runs, and how it is written; to write the new behavior.
- bulk: `Batch.save`, `load` and the `Refused` record, 30 lines with Javadoc, in one
  `add_declaration`: new behavior.
- hand: the docs (contract, notes, CLAUDE.md, roadmap row) through a Python script, not
  `replace_text`; not Java.

## Measured

From `bin/session-report`: 18 tool calls; 5 edit steps through jrefactor (one batch of 3, one
`replace_text`, one `add_declaration` of the test), none refused; 5 `inspect` reads; one large
write (above); by hand only the record's first write and the docs.

## Assessment

- **Through the tools:** every Java change went through `add_declaration`, `replace_text` and
  one `batch`; the import fix wrote `IOException`'s import after a `replace_text` shortened the
  qualified names.
- **Did the plan hold?** Yes, as written: the test, then `save`/`load` on `Batch` and two lines
  in `Cli.run`.
- **Reading:** only what the new code had to match.
- **One change that would have helped:** none needed for this item.

## Follow-ups

- None. The next of the user's four items is the batch refusal for arguments beside `tool`.
