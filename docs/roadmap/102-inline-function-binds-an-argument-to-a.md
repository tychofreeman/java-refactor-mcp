---
order: 95
item: 102
status: Done
---

## Item

`inline_function` binds an argument to a local at the call when inlining it in place would change behavior (an effect run in another order, more than once, or only sometimes), as a standard inline does, instead of refusing

## Status

Done (2026-11-05), local session: `InlineFunction.bindings`; nine refused goldens now apply (`inline_function_binds_*`), the warehouse trap inlines in one call each (`inline_function_binds_short_circuit`; the A/B's hidden test passes on its output); `inline_function_refused_bind_crosses` when the binding would cross an effect; record in `docs/sessions/inline-binds-arguments.md`

## Evidence

A/B rerun (`docs/sessions/ab-warehouse-2.md`): `warehouse-inline` cost the mcp arm 17.8 turns against 10.5. Each of `both`, `either` and `within` was refused (correctly), "put one of them in a variable first" left the order to the model, and extracting `charge(truck)` before `reserve(truck)` was refused as a reorder; 20 refusals in 4 runs
