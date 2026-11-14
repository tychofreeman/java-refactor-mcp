---
order: 251
item: 261
status: Done
---

## Item

The signature tools by `line` (`ByLine.named`) on a line declaring two methods, without `column`, took the last one silently, where two calls on a line are refused (issue #485, code review)

## Status

Done 2026-11-13 (`ByLine.named` keeps every declaration on the line and refuses more than one, listing each with its column, as for calls; golden `add_parameter_refused_two_declarations_on_line`)

## Evidence

Issue #485; `docs/sessions/fix-by-line-two-declarations.md`
