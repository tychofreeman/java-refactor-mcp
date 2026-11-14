---
order: 388
item: 398
status: Done
---

## Item

`TypeSwitch.switchCases` and `EnumSwitch.arms` read a case's body by near-copies of one block, with the same refusal, and `EnumSwitch` placed the switch's call (in the enum's file or the caller's) by one block written twice (issue #517, code review)

## Status

Done 2026-11-14: `TypeSwitch.armBody(statements, i, value, blockOrThrow)` (the enum form passes true: a block in a switch expression and a bare `throw`, #435) and `EnumSwitch.At.placeCall(eEdits)`. No behavior change: every `type_switch*` golden and probe passes unchanged; `TypeSwitchTest` covers `armBody`

## Evidence

Issue #517; `docs/sessions/switch-arms.md`
