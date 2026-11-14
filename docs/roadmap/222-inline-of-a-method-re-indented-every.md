---
order: 213
item: 222
status: Done
---

## Item

`inline` of a method re-indented every line of the moved body, those inside a text block too: a text block whose content or closing delimiter sat left of the body's indentation was shifted unevenly, and the string changed (issue #477, code review)

## Status

Done 2026-11-13 (`InlineFunction.reindent` leaves the lines inside each text block of the body as they are, as `Extraction.reindent` does; probes `inline_function_text_block{,_value}`, golden `inline_function_text_block`. The shared helper is the Indent issue. Default to reopen in Decisions)

## Evidence

Issue #477; `docs/sessions/fix-inline-text-block.md`
