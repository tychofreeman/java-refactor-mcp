---
order: 47
item: 54
status: Done
---

## Item

Imports fixed only for a file under a source root: `create_file` of a file outside the source roots (a golden's expected file) was refused for an ambiguous name it does not need to resolve

## Status

Done

## Evidence

Friction log 2026-11-03 (the `move_type` golden). `Edits.gated` applies the text as written when `SourceProject.packageOf` is null. Record: `docs/sessions/imports-source-root.md`
