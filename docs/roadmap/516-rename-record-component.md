---
order: 506
item: 516
status: Done
---

## Item

`rename` of a record component, by `Type.component` or by line: the component, its field, its accessor and every call of it, and the canonical or compact constructor's parameter (issue #659)

## Status

Done (2026-11-15): `Rename.renameComponent`, routed from both forms of `run`; by name the component is found in the record's header (`componentNamed`) before `Declarations.find`, which knows no components. Goldens `rename_record_component_by_name`, `_by_line`, `_by_call_line` (a method reference in another file), `_written_accessor`, `_canonical` (another constructor's parameter of that name kept), `_compact`, `_refused_clash`, `_refused_local`. `StrategySubclass`'s components were renamed through it

## Evidence

Issue #659 (friction log, 2026-11-15, #528 item 7): by name refused as "has no member subKey"; by line the component was renamed as a parameter and the accessor calls were not, so the gate refused
