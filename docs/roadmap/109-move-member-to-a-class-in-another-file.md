---
order: 102
item: 109
status: Done
---

## Item

`move_member` to a class in another file, and of static fields (constants included) as well as methods

## Status

Done 2026-11-06: methods into another file's class, and static fields (`MoveMember.field`)

## Evidence

Friction triage: two moves into another file's class done by hand (2026-11-03, the end-of-batch check into `Gate`, the file writers into `Journal`); `move_member` moves only methods, within one file. Static fields added 2026-11-05 (the user): a constant cannot be moved to another class at all today, while Go and TypeScript move one with `move_declaration`
