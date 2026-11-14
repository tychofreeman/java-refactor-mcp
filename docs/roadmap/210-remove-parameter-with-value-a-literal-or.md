---
order: 202
item: 210
status: Done
---

## Item

`remove_parameter` with `value` a literal or field of another type than the parameter pasted the value into each read, which then computed in the value's type: `d / 2` became `5L / 2`, an `int` constant made `seconds * 1_000_000_000` overflow, both type-checked (issue #463, code review)

## Status

Done 2026-11-13 (`Signatures.sameType`: when the field's type, or the literal's at any call, is not the parameter's by key, or no call shows the literal's type, the removal goes through `removeWithExpression`, whose body starts with a local of the parameter's type; probes `remove_parameter_value_literal_type` and `_field_type`, golden `remove_parameter_value_field_type`. Default to reopen (the owner said take the smallest): the typed local only when the types differ, over always routing literals and fields through the expression path, which would change every pasted read, and over pasting a cast `((double) 5L)`)

## Evidence

Issue #463; `docs/sessions/fix-remove-parameter-value-type.md`
