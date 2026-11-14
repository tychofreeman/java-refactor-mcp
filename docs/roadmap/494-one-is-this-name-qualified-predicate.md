---
order: 484
item: 494
status: Done
---

## Item

One "is this name qualified" predicate, `Nodes.qualified(SimpleName)`, by the name's place in its parent, in place of five that disagreed (`NameCheck`, `InlineFunction`, `MoveType`, `MoveMember`, `SubclassToStrategy`)

## Status

Done (2026-11-14), issue #505: `subclass_to_strategy` no longer reads `String::trim` in the body as a use of the instance, and refuses `super::m` by that name; `inline` refuses a body with `super::m` as it refuses `super.m()`; goldens `subclass_to_strategy_method_reference`, `subclass_to_strategy_refused_super_reference`, `inline_refused_super_method_reference`; `NodesTest.aNameAfterAQualifierIsQualifiedWhereverItStands`

## Evidence

Code review of 2026-11-13, moves #10 (issue #505). Reproduced: `subclass_to_strategy` of a body with `map(String::trim)` refused "uses trim, a member of the instance"; `inline` of a method returning `super::name` into another class refused with the compile error it wrote ("The target type of this expression must be a functional interface")
