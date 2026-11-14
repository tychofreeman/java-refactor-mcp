---
order: 200
item: 208
status: Done
---

## Item

`push_down`'s override check missed what the moved method overrides when `name` had parentheses (`Sub.hello()`, compared with the bare method name), in a superclass's superclass, and in an interface's default method: the move type-checked and the class then ran the inherited method (issue #465, code review)

## Status

Done 2026-11-13 (`PushDown.refuseOverride` asks `CallTargets.overridden` of the method's binding: every supertype, transitively, abstract methods and `Object`'s included, which the old check skipped; probe `push_down_grandparent_override`, goldens `push_down_refused_override_parens` and `_default`)

## Evidence

Issue #465; `docs/sessions/fix-push-down-override.md`
