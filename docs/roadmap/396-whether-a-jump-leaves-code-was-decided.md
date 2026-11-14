---
order: 386
item: 396
status: Done
---

## Item

Whether a jump leaves code was decided twice, and `TypeSwitch.leaves`, the weaker copy, ignored labels (a `break outer` inside a loop of a case read as staying, and the gate then refused the result as compile errors) and local classes (a `return` in a local class's method was refused); whether a statement can complete normally was decided three ways (`RemoveElse.canFinish`, `ConvertLoop.ends` without `yield`, which left an update after a `yield` that the gate refused) (issue #515, code review)

## Status

Done 2026-11-14: `Effects.exits(jump, root)` (by `lands`, which now takes any node; `Effects.leaves` calls it) for `TypeSwitch.usesOnly`, and `Effects.completesNormally(statement)` (a jump, a block ending in one, an if/else whose branches each cannot) for `RemoveElse` and `ConvertLoop`. Defaults: `completesNormally` reads an if/else, so `remove_else` now takes a then-branch ending in one (over the old last-statement rule); `MoveStatement.isJump` (the statement itself is a jump) and `Extraction.terminates` (returns or throws on every path: a value is always returned) keep their own rules, as they ask other questions. Goldens `type_switch_to_method_refused_labeled_break`, `type_switch_to_method_local_class`, `convert_loop_for_to_while_yield`, `remove_else_then_ends_in_if_else` (each failed before); `EffectsTest`

## Evidence

Issue #515; `docs/sessions/leaving-jumps.md`
