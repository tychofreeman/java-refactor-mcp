---
order: 505
item: 515
status: Done
---

## Item

`move` of a static method holding an anonymous or local class moves it, where it was refused as if the method had a receiver (issue #660)

## Status

Done (2026-11-15): `MoveMember.checkBody` refuses an anonymous or local class only in an instance method, whose `receiver` parameter the class's `this` is not; a static method's class moves as written, its names judged by `NameCheck` and qualified by `movedEdits` as the method's own. Goldens `move_member_static_anonymous` (an anonymous visitor overriding two methods, a field of its own, a static field of the old class qualified), `move_member_static_local_class`; `move_member_refused_anonymous` (an instance method) still refused. Probe `move_member_static_anonymous`

## Evidence

Issue #660 (friction log, 2026-11-15, #528 item 2): moving `Signatures.family` to `MethodFamily` took a rewrite of its anonymous `ASTVisitor` as a `Nodes.all` loop first
