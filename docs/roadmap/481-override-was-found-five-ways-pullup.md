---
order: 471
item: 481
status: Done
---

## Item

`@Override` was found five ways (issue #506, code review): `PullUp`, `AddResult` and `Generated` took any annotation ending in `Override`, so `add_result` refused a method marked `@MayOverride` as an override; `MoveMember` and `PushDown` took the simple name only, so `move` of a method marked `@java.lang.Override` was not refused as an override (it failed the gate with another message)

## Status

Done 2026-11-14: `Nodes.overrideAnnotation(BodyDeclaration)`, by the annotation's resolved type (`java.lang.Override`) and, unresolved, by its name (`Override` or `java.lang.Override`); the five sites call it (`NodesTest`). Goldens `add_result_custom_override_annotation`, `move_member_refused_override_qualified`. `move` and `push_down` still go by the annotation alone where an override has none (`Hierarchy`, #495)

## Evidence

Issue #506; `docs/sessions/override-annotation.md`
