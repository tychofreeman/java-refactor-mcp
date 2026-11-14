---
order: 226
item: 234
status: Done
---

## Item

`Journal.put` removed every directory a deletion left empty with no bound: `move` with `delete=true` of a plain project's only file deleted the project's root, and an undone or refused `create_file` in an empty `src/test/java` deleted that source root (issue #479, code review)

## Status

Done 2026-11-13 (`Journal.removable` stops at a source root and the project's root; `put` and `restore` take the project, added by `add_parameter`. `JournalTest`. Default to reopen in Decisions: no record of created directories)

## Evidence

Issue #479; `docs/sessions/fix-journal-parent-dirs.md`
