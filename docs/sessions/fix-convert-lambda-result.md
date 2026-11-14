# convert_lambda: the result names a block lambda

- **Item:** issue #552 (found while fixing #472; roadmap row 331, order 321)
- **Date:** 2026-11-14
- **Branch:** fix-convert-lambda-result

## Plan

A golden case with `result.txt` for an anonymous class to a block lambda; then name a result of
several lines by `to`, not always as an anonymous class.

## Notes

- read: the golden's edit was right; the result said `to an anonymous class`.
- plan: one `replace_text` in `ConvertLambda.run`.
- hand: the golden resource files.

## Measured

Not measured: the session-log hook is not loaded here. The Java change through `bin/jrefactor`.

## Assessment

Through the tools; the plan held.

## Follow-ups

None.
