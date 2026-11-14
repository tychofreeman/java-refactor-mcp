---
order: 384
item: 394
status: Done
---

## Item

"The node of a type around this one" was written as private helpers (`Extract.ancestorAbove`, `SubclassToStrategy.ancestor`, `Stubs.ancestorOrSelf`, `FieldOrder.ancestor`, `Introduce.enclosing`, `Build.around`, `Fold.typeAround`, `Guards.strip`) and inline parent loops; one of these, `ExtractVariable.enclosingStatement`, walked off the root for an expression in a field's initializer, so `extract_variable` there threw a `NullPointerException` (issue #508, code review)

## Status

Done 2026-11-14: `Nodes.ancestor`, `ancestorOrSelf`, `enclosing` (stops at a lambda or an anonymous class), `typeAround` and `unparenthesized`, moved there by `move` and called by `Build`, `ConvertLambda`, `ExtractVariable`, `Extraction`, `FieldOrder`, `Fold`, `Guards`, `InlineFunction`, `Introduce`, `MoveMember`, `ParameterValue`, `Stubs`, `SubclassToStrategy`, `ThrowsCallers`. `extract_variable` of an expression outside a statement is refused unless `as=constant`, which works there (default to reopen: `as=field` refused, since the field would be declared after its reader). Goldens `extract_variable_field_initializer_refused`, `_refused_field`, `_constant` (each failed before); `NodesTest`. Left: `Rename`, `TypeSwitch` and `Primitives` loops (other open work there), `MoveMember.owningType` and `Build.owner` (two kinds), `ParameterValue.unwrapped` (takes an `Object`); the review's one-level strippers (`ChangeSignature.quietAndSafe`, `NonNull.mayBeNull`) recurse, so they strip every level

## Evidence

Issue #508; `docs/sessions/nodes-ancestor.md`
