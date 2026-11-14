# Compile errors as records (#523)

- **Item:** issue #523, from the code review of 2026-11-13 (group 5 of #532): compile errors
  passed around as `"rel | message"` strings and parsed back.
- **Date:** 2026-11-14
- **Branch:** problem-record

## Plan

A record `TypeCheck.Problem(rel, message)` as the count's key, `introduced` returning
`TypeCheck.Added(problem, extra)`, rendered as text in `Gate.refusal` only; `TypeCheck.FORMAT`
bumped, counts saved as objects. First a failing test for the review's failure case, a path that
holds `" | "`.

## Notes

- read: `TypeCheck`, `Gate`, `Stubs.declare`/`files`, and every test that reads a count.
- red: `TypeCheckTest.aPathWithTheSeparatorIsCountedAgain`, a file under `odd | dir/` fixed after a
  count: the narrowed count kept its error (`expected {} but was {odd | dir/A.java | ...=1}`).
- refusal: the first batch (TypeCheck alone) listed every caller that no longer compiled; the
  retry added the Gate, Stubs and test steps.
- refusal: a retry step's `"\\n"` became a newline: the heredoc was unquoted (`<<JSON`, to expand
  paths), which turns `\\` into `\`. Resent that step with `<<'JSON'` (the session notes' trap).
- No Java by hand: one `batch` of 34 steps with two retries, `add_declaration` for the tests.

## Measured

Not measured (no session-log hook); `JREFACTOR_LOG` holds the CLI calls.

## Assessment

Through the tools. The refusal's error list was the to-do list for the retry.

## Follow-ups

- None.
