---
order: 380
item: 390
status: Done
---

## Item

`rename` of a field, a method and a type were three copies of one loop (a `Holding`, the uses by a predicate, the texts, lines and positions), and every rename result said `%d occurrences`: a member nothing uses was "1 occurrences" (issue #513, code review)

## Status

Done 2026-11-14: `Rename.renameUses` (the predicate, a per-file check, and the file a type's rename moves) returns a `Written`; each rename keeps its own checks. `Names.plural` for the count (`NamesTest`); `checkMeanings` maps offsets back by `NameCheck.before`, its own copy (`Rename.before`) deleted (`NameCheckTest`). Goldens `rename_field_unused`, `rename_parameter_unused`. The issue's `describe` wording ("the field Counter.count" against "the field count of Counter") is left to #501, one label format

## Evidence

Issue #513; `docs/sessions/rename-uses.md`
