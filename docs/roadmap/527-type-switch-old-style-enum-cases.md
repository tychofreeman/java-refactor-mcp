---
order: 517
item: 527
status: Done
---

## Item

`type_switch_to_method` over an enum refused a switch statement of old-style cases (`case RED: return "r";`), the usual shape in older code, naming no fix a tool does (issue #374; the enum sketch's increment 12, question 7 option (a))

## Status

Done 2026-11-17 (`ColonSwitch`: a switch statement over an enum whose every group ends in `return` with a value or `throw` is read as `return switch (...) { arrows };` and converted by `EnumSwitch` as before; without a `default` every constant needs a case and the statements after the switch, which none reaches, are removed. Fall-through, a `break` or `continue` leaving a case, `return;`, and a local shared between groups are refused. Goldens `type_switch_enum_colon_*`, probe `type_switch_enum_colon`)

## Evidence

`docs/sessions/enums-sketch.md`, example 3.5; the user asked for the open issues on 2026-11-17
