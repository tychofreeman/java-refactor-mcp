---
order: 132
item: 139
status: Done
---

## Item

A field an edit adds goes before a field whose initializer reads it, not after (issue #357)

## Status

Done 2026-11-11 (`FieldOrder`, in `Stubs.declare`: a forward reference to a field the change added moves it before its reader, in one call or at a batch's end)

## Evidence

Friction log 2026-11-11, "add_declaration put a Def after the list that names it"; `docs/sessions/place-before-reader.md`
