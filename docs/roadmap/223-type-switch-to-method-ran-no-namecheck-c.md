---
order: 215
item: 223
status: Done
---

## Item

`type_switch_to_method` ran no `NameCheck`: `c.radius` became `radius`, which a local of the case's body captured, and a field of the switch's class named as a local elsewhere in the body passed as one

## Status

Done 2026-11-13 (`TypeSwitch.run` calls `NameCheck.require` over every file's edits with the case bodies' meanings; `usesOnly` tells the body's own locals by binding key, not identifier (shared with `EnumSwitch`); `asReceiver` writes `this.x` for `c.x` when the body declares an `x`. Probes `type_switch_field_capture`, `type_switch_field_by_name`, `type_switch_static_capture`; goldens `type_switch_statement_field_local`, `type_switch_to_method_refused_field_by_name`, `type_switch_to_method_refused_static_capture`). Default to reopen (the smallest fix): `this.x` only where a local of that name is declared, over always `this.x` and over refusing

## Evidence

Issue #468 (code review of 2026-11-13); `docs/sessions/fix-type-switch-names.md`
