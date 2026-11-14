# Line and offset arithmetic through Lines

- **Item:** issue #512 (code review of 2026-11-13, core #13 and extract-inline #6), group 4 of
  tracking issue #532
- **Date:** 2026-11-14
- **Branch:** lines-helpers

## Plan

Pure consolidation. `Positions.offset` moves into `Lines` as `offsetOf(text, line, column)`, its
refusals kept; each private copy (`Imports.count`, `Inspect.end`, `Extraction.indentOf` and
`lineStartOffset`, `Extract.lineEndOffset`, `InlineVariable.removeLine`'s bounds) gives way to
`Lines` or `Nodes.end`. A `LinesTest` case for the new overload; every golden and probe stays green.

## Notes

- read: `inspect` with `names` for each site, `pattern` for callers.
- already done on `main`: `Opened.startingOn` uses `onLine` (`unit.getLineNumber`), and
  `InlineFunction` uses `MemberMove.removal`; `ExtractVariable`'s hand line end is gone. Nothing to
  reproduce: the double blank line the review named went with `InlineFunction.removal`.
- tools: one batch of 13 steps, two `inline`s, one `replace_text`, `add_declaration` for the test.
- hand: docs only.

## Measured

Not measured: the session-log hook is not loaded here (the CLI log is).

## Assessment

Through the tools.

## Follow-ups

- `Declarations`, `RemoveElse` and `IndentShift` still count lines by hand (outside the review's
  scope).
