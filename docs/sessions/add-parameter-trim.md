# add_parameter's description trimmed, over the limit by design

- **Item:** roadmap item 523, the owner's choice of 2026-11-16
- **Date:** 2026-11-16
- **Branch:** add-parameter-trim

## Plan

Apply the draft from `tool-descriptions-trim.md` (2,396 characters, every route's example kept),
chosen by the owner over splitting out the type parameter route and over leaving it at 3,338;
lower its `DEFINITION_OVER` entry to the new size.

## Notes

- One `replace_declaration` of `Tools.ADD_PARAMETER`, generated from the measured draft as in the
  first PR; the `value` literal and the `values` JSON line were joined and wrapped in the batch's
  section before it was sent.
- hand: none. refusal: none. The `values` text block's wrapped line came out at column 0 (the
  section's line had no indentation); three `replace_text` calls put it right, since
  `replace_text` moves `new` by its first line's indentation and keeps each later line's offset
  from it (friction log).
- `ServerTest.DEFINITION_OVER`: `add_parameter` 3,338 to 2,396 by `replace_text`.

## What went, what stayed

Kept: the plain example with lambdas (`(a, b) -> a` becomes `(a, b, retries) -> a`), the record,
enum (`values`) and type parameter examples for a class and a method. Went: "Every declaration it
overrides or that overrides it" as a sentence (now "overrides and implementing lambdas too");
the addressing sentence (`m(int, String)`, a constructor by its type's name: the `name` argument
keeps the overload form, not the constructor hint); "value is a Java expression every call can
see, with no side effects" (a refusal); the `members` example (`value, get, Box(String)`).

## Measured

Not measured: the session-log hook is not loaded here. 5 tool calls wrote Java (1 batch, 4
`replace_text`), 0 hand edits.

## Assessment

Through the tools; the indentation fix cost two extra calls.

## Follow-ups

- The constructor hint ("a constructor by its type's name") left the definition; if a model
  misses it, it fits in the 2,396 budget's slack only by raising the entry.
