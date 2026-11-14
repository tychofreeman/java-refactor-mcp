# remove_parameter value of another type than the parameter

- **Item:** issue #463 (code review of 2026-11-13), roadmap #210
- **Date:** 2026-11-13
- **Branch:** fix-remove-parameter-value-type

## Plan

`remove_parameter` with `value` a literal or field pastes the value into each read. When the
value's type is not the parameter's, the reads compute in the value's type. The expression path
(`removeWithExpression`) already declares a local of the parameter's type first; route a literal
or field whose type differs there. One helper (`sameType`) and one branch in `removeWithValue`.

## Notes

- read: `inspect` with `pattern` and line ranges of `Signatures.java`.
- plan: probes first (`remove_parameter_value_literal_type`, `_field_type`), both failed as the
  issue says (2.5 printed 2.0; 5000000000 overflowed).
- hand: the probe's `value=1` was read by `ProbeTest` as an integer ("value must be a string");
  used `5L` instead.
- `add_declaration` of `sameType`, `replace_text` of the branch: both through `bin/jrefactor`.
- refusal: `replace_text` with `old_file=-` and `new_file=-` (only one argument from stdin);
  sent `old` and `new` inline.
- golden `remove_parameter_value_field_type` pins the output (`long seconds = STEP;`).

## Measured

Not measured: the session-log hook is not loaded here. Two tool edits to Java, none by hand.

## Assessment

Through the tools. The plan held: the fix is a route to existing code.

## Follow-ups

- A literal with no call to show its type always goes the expression way (a local), the safe side.
