---
order: 504
item: 514
status: Done
---

## Item

`move` of a static method qualifies the member types of its old class that it names simply, and the import fix never takes a JDK class a project's code cannot import (issue #667)

## Status

Done (2026-11-15): `MoveMember.qualifyMemberTypes` writes `Signatures.Call` for `Call` (a member type of the class or a superclass, in the signature and body, Javadoc left alone); `NameCheck` refuses a written simple name that meant a member type and resolves to nothing (`inline` hits it); `Imports` indexes only the JDK packages exported to every module, less `sun.*` and `com.sun.*`. Goldens `move_member_static_member_type` (the wrong type, `java.util.Stack`, would compile), `move_member_static_member_type_signature`, `inline_function_refused_member_type`, `imports_never_jdk_internal`; probe `move_member_static_member_type`

## Evidence

Issue #667 (friction log, 2026-11-15, #528 item 2): moving a static method of `Signatures` that named `Call` imported `jdk.vm.ci.code.site.Call`; the gate refused only because the types did not fit
