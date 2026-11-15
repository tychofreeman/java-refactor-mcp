---
order: 503
item: 513
status: Open
---

## Item

`move` of a member type (a nested record, enum, interface or class) into another class: `move name=Signatures.Decl to=MethodFamily`, every reference rewritten to the new place. Options and what it must handle: `docs/sessions/member-type-move-sketch.md`; the owner chooses before it is built.

## Status

Open (2026-11-15): options written for the owner's choice.

## Evidence

Issue #528 item 2: the records `Source`, `Decl`, `Call`, `Lambda` and `Reference` should move from `Signatures` to `MethodFamily` (about 100 lines in seven files name them). `move` with `to` refuses a member type ("declares no method or field Decl"), and nothing nests a top-level type (friction log, 2026-11-15, signatures-family).
