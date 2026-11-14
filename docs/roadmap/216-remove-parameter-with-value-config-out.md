---
order: 208
item: 216
status: Done
---

## Item

`remove_parameter` with `value=Config.OUT` wrote each read as the bare `OUT`, which bound to the method's class's own `OUT` when it had one, type-checked, and changed behavior (issue #464, code review)

## Status

Done 2026-11-13 (`Signatures.readOf` writes the bare name only when the first field of that name in the method's class or a superclass is the value's field (`firstNamed`, by key) and no local hides it, else `Type.NAME` by `Extract.typeName` from the method's class, or `this.name`; probe and golden `remove_parameter_value_other_constant`; numbered 211 until it met `invert_if`'s 211 on `main`. Default to reopen (the owner said take the smallest): qualify by that rule over running `NameCheck` on the substituted reads, and over always qualifying a static field, which would change the pasted reads of the class's own constants)

## Evidence

Issue #464; `docs/sessions/fix-remove-parameter-value-qualified.md`
