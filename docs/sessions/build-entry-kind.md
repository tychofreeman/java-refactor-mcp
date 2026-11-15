# Build.Entry's kind as an enum (#528, item 3)

- **Item:** issue #528, item 3: `Build.Entry` was a mutable bag discriminated by a string `kind`
  (15 `kind.equals` tests). The owner chose option (C) on 2026-11-15: keep one class, an enum and
  switches; no BuildSpec/Draft split (options in `docs/design.md`, Decisions).
- **Date:** 2026-11-15
- **Branch:** build-entry-kind

## Plan

`add_declaration` of `enum Kind` in `Build` (constants for the five keys, `toString` the key so
every refusal's text is unchanged, `guard()` for the two guards), the field's type changed by
`replace_text`, and each test rewritten by `replace_expression` (`e.kind == Kind.LOOP`), the
kinds' list and the arguments each kind takes by `replace_statements` (a switch), all in one
batch checked at the end.

## Notes

- One batch of 20 steps, applied as planned from one read.
- No refactoring changes a String field to an enum with its tests (friction log).

## Measured

Not measured (no session-log hook). 1 batch: `add_declaration`, `replace_text`, 16
`replace_expression`, 2 `replace_statements`; no Java by hand.

## Assessment

Through the edit tools. Every string test of the kind is gone; the build goldens pass unchanged.

## Follow-ups

None.
