---
order: 222
item: 230
status: Done
---

## Item

`replace_text` with an empty `old` never ended: `indexOf("", i)` is `i`, so the match loop added offsets until the heap ran out, and the server, one call at a time, answered nothing more (issue #462, code review)

## Status

Done 2026-11-13 (a precondition in `Edits.replaceText` refuses an empty `old`, naming `append=true`; golden `replace_text_refused_empty_old`)

## Evidence

Issue #462; `docs/sessions/fix-replace-text-empty-old.md`
