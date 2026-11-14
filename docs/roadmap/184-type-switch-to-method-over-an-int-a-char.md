---
order: 177
item: 184
status: Done
---

## Item

`type_switch_to_method` over an `int`, a `char` or a `String` was refused for its cases ("each case must be a type pattern", or "an arrow case giving an expression"), a fix that cannot apply; the reason is the selector (issue #437)

## Status

Done 2026-11-13 (`TypeSwitch.run` checks the selector's type before reading the cases; the refusal names an enum too and says what to do instead: leave the switch, or `extract_function` it. A wording fix: what is refused is unchanged)

## Evidence

The Java 17 stress run, `docs/sessions/stress-enum-switch.md` (306 of 750 calls); `docs/sessions/fix-switch-selector-wording.md`
