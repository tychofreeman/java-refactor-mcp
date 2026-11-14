---
order: 40
item: 47
status: Done
---

## Item

`Gate.restore` and `Journal.restore` both write or delete files, with different parent-directory handling; `Gate.put` is a third writer

## Status

Done

## Evidence

Full code review 2026-11-03. `Journal.put` and `Journal.restore` are the one writer; `Gate` calls them. Record: `docs/sessions/file-writers-one-place.md`
