---
order: 146
item: 153
status: Done
---

## Item

A case block with `yield` in a switch expression over an enum, refused before (the enum sketch, slice 3, increment 7)

## Status

Done 2026-11-13 (`TypeSwitch.ownYield`: a yield whose innermost switch expression is the converted one becomes `return` in `TypeSwitch.asReceiver`, and `usesOnly` lets it through; a yield to an outer switch stays refused)

## Evidence

`docs/sessions/enums-sketch.md`, slice 3; `docs/sessions/enum-switch-yield.md`
