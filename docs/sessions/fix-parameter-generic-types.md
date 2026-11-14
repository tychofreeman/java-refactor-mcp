# extract_variable as=parameter: methods with generic parameter types

- **Item:** roadmap #180 (order 173), issue #406 from the stress run on Commons Lang:
  `extract_variable` `as=parameter` refused every method whose parameter types are generic.
- **Date:** 2026-11-13
- **Branch:** fix-parameter-generic-types

## Plan

`Introduce.parameter` named the method for `Signatures.addParameter` by `Signatures.label`
(erasures: `apply(Function, Object)`), which `Declarations.find` matches against the types as
written (`apply(Function<T,R>, T)`). Name it as `ByLine.named` does, by the declaration:
`Names.typeLabel(class) + "." + MemberMove.label(m)`. One `replace_text`, after the reproduction
goldens.

## Notes

- read: the issue, `ByLine.named`, `MemberMove.label`.
- plan: goldens `extract_variable_parameter_generic` (a type variable and a parameterized type;
  the parameter lands after `f`, #172), `_generic_list`, and `_varargs` (now refused by
  `MethodFamily`'s "has varargs", which the tools do not change yet). Red with the issue's message.
- plan: the other paths: `Signatures.label` is passed as a `name` nowhere else (the issue checked);
  `remove_parameter` `value` takes the model's name. No other change.
- hand: none in `src/main`.

## Measured

Not measured: the session-log hook is not loaded here. 1 `replace_text` into `src/main`, 1 batch for
docs; 3 goldens with file tools.

## Assessment

Through the tools; the plan held.

## Follow-ups

- Varargs methods are still refused by `MethodFamily` for every signature tool.
