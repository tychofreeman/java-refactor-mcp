---
order: 51
item: 58
status: Done
---

## Item

`move_type` drops an `import a.Outer` that a file in another package no longer uses once it imports `a.Inner`

## Status

Done, local session

## Evidence

Follow-up of `docs/sessions/move-type.md`. `MoveType`: such a file's imports go through `Imports.fix` with the moved type chosen. Record in `docs/sessions/move-type-unused-import.md`
