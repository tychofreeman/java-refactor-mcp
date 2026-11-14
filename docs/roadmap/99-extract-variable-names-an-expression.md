---
order: 92
item: 99
status: Done
---

## Item

`extract_variable` names an expression with effects when nothing evaluated before it in its statement reads what it could change: the first `p.bin()` of `transfer(p.bin(), p.bin(), p.qty())`, `reserve(truck)` in `both(reserve(truck), charge(truck))`

## Status

Done (2026-11-05), local session: by moving `extract_variable` onto the behavior gate (#96; `extract_variable_first_effect`)

## Evidence

A/B run (`docs/sessions/ab-warehouse.md`): refused in every mcp run of `warehouse-reorder` and in two of `warehouse-inline`, each then rewritten with `replace_statements`; the refusal had told it to do exactly that extraction. `ExtractVariable.checkPlace` refuses any expression with effects
