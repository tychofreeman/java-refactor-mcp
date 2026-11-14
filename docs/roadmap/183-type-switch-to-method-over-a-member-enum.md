---
order: 176
item: 183
status: Done
---

## Item

`type_switch_to_method` over a member enum refused a case naming a static of its enclosing class (`case CLASS -> CLASS_FLAGS;`) as one the enum "does not see by that name", though it does (issue #436)

## Status

Done 2026-11-13 (`EnumSwitch.sees`: the static's class is the enum or a type around it, and no type on the way out declares a member of that name, so a constant that hides it is still refused with the qualify fix. A default to reopen: a static the enclosing class inherits from its own supertypes is still refused, over walking each enclosing type's supertypes)

## Evidence

The Java 17 stress run, `docs/sessions/stress-enum-switch.md` (33 calls on the backport's `AccessFlag.Location`); `docs/sessions/fix-enum-outer-static.md`
