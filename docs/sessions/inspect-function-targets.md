# inspect function and targets

- **Item:** issue #369, its second part (`function`, `targets`); roadmap #505
- **Date:** 2026-11-15
- **Branch:** inspect-function-targets

## Plan

`function` is `names` limited to one file (an `only` parameter added by `add_parameter`, its
caller passing null), plus a bare method name turned into `Type.method`; `targets` calls
`Tools.inspect` once per target and puts a refusal in its place, as gorefactor's `EachTarget`.

## Notes

- plan: five goldens first; red (four errors, one wrong refusal).
- bulk: one batch (`add_parameter`, `replace_text`, `add_declaration`) in `Inspect`; one batch of
  three `replace_text` in `Tools`.
- refusal (test): `definitionsStaySmall`, 1949, 1751, 1707 characters against 1700: the
  description lost its examples and the role list was cut to four.
- hand: none in Java.

## Measured

Not measured (no session-log hook). Java changes: 6 tool calls, none by hand.

## Assessment

Through the tools. Most of the time went to the description's size.

## Follow-ups

- The body summary (complexity, nesting, side effects), the last part of #369.
