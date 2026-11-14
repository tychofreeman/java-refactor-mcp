---
order: 470
item: 480
status: Done
---

## Item

`Extraction.apply` wrote its edit twice: the text by concatenation, and the same change as two `TextEdit`s for `NameCheck`, so a change to one would leave `NameCheck` judging another edit than the one written (issue #527, code review)

## Status

Done 2026-11-14: the two `TextEdit`s are built once, and the text is `TextEdit.apply` of them, as `ExtractVariable` and `InlineVariable` do. No behavior change; the extract goldens and probes pass

## Evidence

Issue #527; `docs/sessions/extraction-edits.md`
