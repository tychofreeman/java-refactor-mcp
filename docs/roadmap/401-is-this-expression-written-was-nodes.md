---
order: 391
item: 401
status: Done
---

## Item

"Is this expression written?" was `Nodes.written` plus copies in `ExtractType`, `InlineFunction.checkNotAssigned`, `ParameterValue.assigned`, `ConvertLoop.writes` and `Extraction` (`assigned`, `refuseFinalFields`), and none saw a target in parentheses, which Java allows (`(a)++`, `(values[i]) = 0`): `inline` of `next(int a) { return (a)++; }` wrote `(x)++` at the call, changing the caller's `x`; `convert_loop` to a for-each wrote `(value) = 0`, losing the array writes; `extract_variable` `as=parameter` of `qty / 10` after `(qty)++` passed the value before the increment; `extract_type` of a parameter assigned as `(width) = ...` was refused by the gate, not by its own refusal (issue #510, code review)

## Status

Done 2026-11-14: `Nodes.written` looks through parentheses, and the six copies call it (`ConvertLoop.writes` inlined). Refusal wording unchanged. Probes `inline_function_parenthesized_parameter_write`, `convert_loop_parenthesized_element_write`, `extract_variable_parameter_parenthesized_write`; goldens `inline_function_refused_parenthesized_parameter_write`, `convert_loop_refused_parenthesized_element_write`, `extract_variable_parameter_refused_parenthesized_write`, `extract_type_refused_parenthesized_assignment` (each failed before); `NodesTest.aWriteIsSeenThroughParentheses`. Left: `EncapsulateField`, `Stubs`, `Generated`, `ChangeSignature.statement`, `InlineFunction.statementExpression` and `ConvertLoop`'s update test ask other questions (a statement expression, an operand's type, a loop step); `Effects` keeps its own walk

## Evidence

Issue #510; `docs/sessions/nodes-written.md`
