---
order: 381
item: 391
status: Done
---

## Item

Putting an item into a comma-separated list was written three times: `Signatures.insertAt` (taking the `(`), `SignatureChange.insert` (taking the offset past it, and a constant with no parentheses) and `TypeParameters.inserted` (a non-empty list only) (issue #514, code review)

## Status

Done 2026-11-14: `SignatureChange.insertAt(list, open, at, item)` and `insertAt(list, at, item)` for a non-empty list; `insert` calls it, and the other two were made delegates and inlined (`EnumField`, `Signatures.addComponent` and the constructions' arguments, `TypeParameters`). No behavior change: an index past the end appends, which only `SignatureChange.insert` did not do and no caller passes (`SignatureChangeTest`)

## Evidence

Issue #514; `docs/sessions/list-insert.md`
