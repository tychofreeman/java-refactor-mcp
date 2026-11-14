---
order: 134
item: 141
status: Done
---

## Item

A member added to an enum that has constants only was written with no `;` after the last constant, so `add_declaration`, the field stub and `type_switch_to_method` onto an enum were refused with a syntax error (the enum sketch, increment 1)

## Status

Done 2026-11-13 (`MemberMove.afterConstants`, used by `Declarations.plan`, through which stubs go, and `MemberMove.appendTo`: the `;` after the last constant, on its own line after a trailing comma or with no constant; a body on one line is opened up with its constants kept, where `appendTo` dropped them)

## Evidence

`docs/sessions/enums-sketch.md`, section 7; `docs/sessions/enum-member-separator.md`
