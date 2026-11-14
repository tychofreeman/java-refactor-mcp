---
order: 29
item: 36
status: Done
---

## Item

Overload parameter lists split at commas inside generics (`Map<String, Integer>` breaks), so `replace_declaration`, `add_parameter`, `remove_parameter` and `move_parameter` refuse such overloads with names the model cannot type back

## Status

Done

## Evidence

Full code review 2026-11-03: reading, not run. `Declarations` splits on `,`
