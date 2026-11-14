# ByLine: refuse a line that declares two methods

- **Item:** issue #485 (code review of 2026-11-13, signatures #20), group 2 of tracking issue #532
- **Date:** 2026-11-13
- **Branch:** fix-by-line-two-declarations

## Plan

Reproduce with a golden case (`add_parameter` by `line` on `int a() {...} int b() {...}`), then
keep the line's declarations in a list and refuse more than one, listing columns as the calls
branch does.

## Notes

- read: `inspect` of `ByLine.named` by line range.
- plan: the golden case applied the change to `b` with no refusal, as the issue said.
- refusal: a batch of three `replace_text` steps was refused at the end check (a fourth use of
  `declared` was left); `batch` with `retry` and one step `after` 3 fixed it without resending.
- hand: the golden files and the docs.

## Measured

Not measured: the session-log hook is not loaded here. Two tool calls to Java, none by hand.

## Assessment

Through the tools; the retry saved resending three steps.

## Follow-ups

None.
