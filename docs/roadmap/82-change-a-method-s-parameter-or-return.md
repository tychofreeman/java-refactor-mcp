---
order: 75
item: 82
status: Done
---

## Item

Change a method's parameter or return type, every override and call following (gorefactor's `change_parameter_type`; Java adds the return type)

## Status

Done (2026-11-04), local session: `change_type` with `parameter` or `return_type`; record in `docs/sessions/change-signature-type.md`

## Evidence

Recommendation 2 (2026-11-04): #125 changed `appendTo`'s return type with an `add` to `addAll` edit at each of four callers. Extends the signature tools; `change_type` does fields only
