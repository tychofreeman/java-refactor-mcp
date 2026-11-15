---
order: 503
item: 513
status: Open
---

## Item

`move` of a member type (a nested record, enum, interface or class) into another class: `move name=Signatures.Decl to=MethodFamily`, every reference rewritten to the new place. Options and what it must handle: `docs/sessions/member-type-move-sketch.md`; the owner chose (A) on 2026-11-15.

## Status

Open (2026-11-15): the owner chose option (A), built in slices (`docs/design.md`, Decisions). Slice 1 is built: a static member type into a top-level class, record or enum of another file of the same package (`NestType`). Left: a target in another package, an interface target, a target in the outer file or nested, inner classes.

## Evidence

Issue #528 item 2: the records `Source`, `Decl`, `Call`, `Lambda` and `Reference` should move from `Signatures` to `MethodFamily` (about 100 lines in seven files name them). `move` with `to` refuses a member type ("declares no method or field Decl"), and nothing nests a top-level type (friction log, 2026-11-15, signatures-family).
