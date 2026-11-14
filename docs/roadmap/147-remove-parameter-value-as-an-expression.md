---
order: 140
item: 147
status: Done
---

## Item

`remove_parameter` `value` as an expression of the method's other parameters, fields, constants and literals, without calls: the query form of `replace_parameter` (`docs/sessions/generators-sketch.md`, slice 4, increment 1)

## Status

Done 2026-11-13 (`Signatures.removeWithExpression`: the body starts with a local of the parameter's name and type; `ParameterValue` holds the parameter rules `Introduce` had and the matcher of a call's argument against the value, `passes`; refused: a call that passes anything else, an argument the value reads that has effects or another primitive type, an argument with effects between or, for a value that reads a field, after; a value that may throw is reported through `Exceptional`)

## Evidence

The sketch; the user's choice of 2026-11-13; `docs/sessions/remove-parameter-value-expression.md`
