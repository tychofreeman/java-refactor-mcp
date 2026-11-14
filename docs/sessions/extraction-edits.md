# Extraction.apply builds its edit once

- **Item:** issue #527 (code review of 2026-11-13, extract-inline #21), group 4 of tracking issue #532
- **Date:** 2026-11-14
- **Branch:** extraction-edits

## Plan

Pure consolidation: the list of two `TextEdit`s first, `edited` as `TextEdit.apply` of it, and
`NameCheck.require` given the same list. The extract goldens and probes must stay green;
`TextEdit.apply` already has its unit test (`TextEditTest`).

## Notes

- read: `inspect` with `names` (`Extraction.apply`).
- tools: one `replace_statements` (lines 102-117 to 11 lines). `extract_variable` of the
  `List.of(...)` argument was not tried: it would declare the list after `edited`, which must then
  read it, so a `move_statement` and a `replace_expression` would follow; one statement edit was
  shorter.
- hand: docs only.

## Measured

Not measured: the session-log hook is not loaded here (the CLI log is).

## Assessment

Through the tools, one call.

## Follow-ups

None.
