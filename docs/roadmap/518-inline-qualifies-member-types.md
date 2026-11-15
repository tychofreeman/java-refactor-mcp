---
order: 508
item: 518
status: Done
---

## Item

`inline` of a method whose body names a member type of its declaring class by its simple name qualifies it at a call outside that class, as `move` does, in place of the refusal of #668 (issue #673)

## Status

Done (2026-11-15): `InlineFunction.replacementFor` takes `inside` (the call is in the declaring class, in its file) and, when it is not, adds `MoveMember.qualifyMemberTypes`'s edits to the receiver's edits, so `new Stack()` is written `new Shop.Stack()`; the import fix imports the outer class where the call's file needs it. Goldens `inline_function_member_type` (was `inline_function_refused_member_type`), `_other_package` (the call through a static import, `Shop` imported in its place), `_inside` (the simple name kept); probe `inline_function_member_type` (`import java.util.*` beside the call: the inlined code constructs `p.Shop$Stack`)

## Evidence

Issue #673, the follow-up of #668 (issue #667)
