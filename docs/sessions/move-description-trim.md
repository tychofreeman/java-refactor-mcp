# move's description to the 1,700-character limit

- **Item:** roadmap item 517, the owner's request of 2026-11-15
- **Date:** 2026-11-15
- **Branch:** move-description-trim

## Plan

Text only: the description and the argument descriptions of `Tools.MOVE`, measured as
`tools/list` sends them. Keep each route's example and what a route does that the schema does not
say; cut what every tool shares (references follow, imports, refusals) and what the description
and the arguments both say. Then drop `move` from `ServerTest.DEFINITION_OVER`.

## Notes

- read: `inspect` of `ServerTest` (`definitionsStaySmall`, `DEFINITION_OVER`) and of `Tools.MOVE`.
- read: the definition as sent, from the jar's `tools/list` over stdin: 1,964 characters, 817 of
  them the description.
- plan: drafts measured outside the code (the JSON with the new text) until under 1,700: 1,678.
- `replace_text` of the text block and the argument descriptions in one batch (literals), and of
  the `Map.entry("move", 1964)` line in `ServerTest` (a few lines of code; no tool removes an
  argument of a call).

## Measured

Not measured: the session-log hook is not loaded here. 2 tool calls wrote Java (both
`replace_text`), 0 hand edits.

## Assessment

Through the tools. The whole change is text, so `replace_text` was the fit.

## Follow-ups

- The other tools in `DEFINITION_OVER` (`fold`, `extract_function`, `batch`, `add_declaration`,
  `inline` are within 200 characters of the limit).
