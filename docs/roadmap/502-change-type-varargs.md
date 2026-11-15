---
order: 492
item: 502
status: Done
---

## Item

`change_type` refused a varargs type: `type=Path...` gave "type Path... does not parse", since the
type was parsed alone and `T...` is a parameter's form, not a type; and a varargs method was
refused outright by `MethodFamily.of`.

## Status

Done 2026-11-15: for a method's last parameter `T...` sets the varargs form with type `T`; a
varargs parameter becomes `T[]`, `T` or another `U...` (`Signatures.changeType`,
`Signatures.varargsElement`; `MethodFamily.of` takes `varargs`, true only for `change_type`).
Calls are not edited; the gate judges them. `T...` on a parameter that is not last, on a return
type or on a field is refused saying why.

## Evidence

Issue #614; friction log (#502, PR #575: `Holding.of`'s `also`, done with `replace_declaration`
and `replace_text` on each caller). Goldens `change_type_parameter_to_varargs` (calls passing an
array, in two files), `change_type_parameter_from_varargs_to_array`,
`change_type_parameter_from_varargs_refused_calls` (several arguments, refused by the gate),
`change_type_parameter_from_varargs_to_element`, `change_type_parameter_varargs_element`
(several arguments kept), `change_type_parameter_varargs_refused_not_last`,
`change_type_return_varargs_refused`, `change_type_field_varargs_refused`.
