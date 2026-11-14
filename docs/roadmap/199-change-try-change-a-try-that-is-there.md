---
order: 192
item: 199
status: Done
---

## Item

`change_try`: change a try that is there (issue #364): add or remove a catch clause or a multi-catch type, add or remove a finally, unwrap the try

## Status

Done 2026-11-13 (`ChangeTry`; the user chose a new tool over options on `replace_statements` and over two tools, all but resources in one PR, and an edit over a refactoring; 12 goldens `change_try_*`); resources in row 206; record in `docs/sessions/change-try.md`

## Evidence

Issue #364, asked for by the user while `change_signature` gained `callers`; until now such changes went through `replace_text`
