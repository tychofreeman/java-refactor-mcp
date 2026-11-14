---
order: 175
item: 182
status: Done
---

## Item

`type_switch_to_method` over an enum refused a case that is a bare `throw` (`default -> throw ...;`, the commonest last arm of an enum switch expression) as not "an arrow case giving an expression" (issue #435)

## Status

Done 2026-11-13 (`EnumSwitch.arms` reads a `ThrowStatement` after the arrow as a block's statements: the method form writes it as the body, the field form takes it as not a constant. The type-pattern form (`TypeSwitch.switchCases`) still refuses it, left until a task needs it)

## Evidence

The Java 17 stress run, `docs/sessions/stress-enum-switch.md` (20 calls on the backport's `BytecodeHelpers`); `docs/sessions/fix-enum-switch-throw.md`
