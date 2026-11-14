---
order: 73
item: 80
status: Done
---

## Item

`move_type` moves a top-level type to another package (an option, `package`): its file moves, its package line and every reference and import follow, and members used across the new package boundary are widened or refused

## Status

Done (2026-11-04), local session: `move_type` with `package`, `MoveToPackage`; record in `docs/sessions/move-type-package.md`

## Evidence

Friction log: "No tool moves a type or…", golden and fixture moves by hand. The user's order of 2026-11-04 (recommendation 1). An option on `move_type`, not a tool, unless its design note finds otherwise
