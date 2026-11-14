---
order: 300
item: 310
status: Done
---

## Item

`add_supertype` on a sealed class with a `permits` clause and no list to join wrote the new `extends` or `implements` just before the body's brace, after `permits`, which did not parse and was refused as the tool's fault (issue #484, code review)

## Status

Done 2026-11-13 (`AddSupertype.run` puts a new clause before the `permits` keyword when `permittedTypes()` is not empty; golden `add_supertype_before_permits`. `MoveType.headerEnd` runs only without a `permits` clause and was left as it is)

## Evidence

Issue #484; `docs/sessions/fix-add-supertype-permits.md`
