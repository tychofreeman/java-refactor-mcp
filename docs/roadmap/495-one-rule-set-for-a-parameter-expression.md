---
order: 485
item: 495
status: Done
---

## Item

The per-call rules for a parameter expression were two copies that had drifted apart (issue #500, code review): `extract_variable` `as=parameter` (`ParameterValue.perCall`) and `remove_parameter` `value` (`Signatures.removeWithExpression`) each checked the arguments the value reads (effects, type, re-read across a call) and the other arguments' order in their own way, and `ParameterValue` compared names by a key of its own (`Bindings.erasedKey`)

## Status

Done 2026-11-14: one `ParameterValue.checkCall(InMethod, Call, Direction)` for both, the owner's option (a) over keeping two copies (Decisions in `docs/design.md`). The differences that remain are the parameters: a cast only towards the calls (`TO_CALLS`), and where the value stands among the arguments. `remove_parameter` `value` no longer refuses an argument with effects between the ones the value reads and the removed one when it cannot change them (probe `remove_parameter_value_argument_between_locals`); `ParameterValue` compares by `Bindings.declarationKey`, and `Bindings.erasedKey` is gone (golden `remove_parameter_value_refused_type_variable`)

## Evidence

Issue #500 (code review of 2026-11-13). Probes `remove_parameter_value_argument_between_locals` (refused before, applied after, same output), `remove_parameter_value_field_between_effect`, `remove_parameter_value_call_reread_later_argument`, `extract_variable_parameter_argument_between_locals`, `extract_variable_parameter_call_placed_last` (each passed before and after)
