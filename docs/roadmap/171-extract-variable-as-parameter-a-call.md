---
order: 164
item: 171
status: Done
---

## Item

`extract_variable` `as=parameter`: a call passing a primitive of another type than a parameter the expression reads gets the argument cast to the parameter's type, in place of #161's refusal

## Status

Done 2026-11-13 (`ParameterValue.cast`, `substituted` with the method's parameters; `remove_parameter` `value` keeps its refusal, which a cast cannot reproduce; the user's choice, "an argument of another type is cast" above)

## Evidence

The owner's decision; `docs/sessions/parameter-value-cast.md`
