# removeWithExpression's refusals to ParameterValue (#528, item 2)

- **Item:** issue #528, item 2's second half: the refusal text of
  `Signatures.removeWithExpression` (220 lines, mostly refusals) into `ParameterValue`.
- **Date:** 2026-11-15
- **Branch:** remove-expression-refusals

## Plan

`extract_function` of each check, bottom first, then one batch of `move` of the five to
`ParameterValue`.

## Notes

- extract_function: `refuseCallsNotPassing` (5), `refuseConstructorValue` (3),
  `refuseConstructorCallRead` (4), `refuseWritingValue` (3), `refuseNoLocal` (5).
- refusal (the gate): `move` of `refuseCallsNotPassing` wrote its `for (Call c : ...)` as it was,
  though `Call` there named `Signatures.Call`, a member type of the class it left; the import fix
  then took `jdk.vm.ci...Call` for it. A `replace_text` (narrowed by `function`) wrote
  `Signatures.Call` first, in the same batch (friction log; a bug in `move`).
- `move` widened each method to package access, which it needs: `Signatures` calls them.

## Measured

Not measured (no session-log hook). 5 `extract_function`, 1 batch (`replace_text`, 5 `move`),
1 refused batch; no Java by hand.

## Assessment

Through the tools. `removeWithExpression` 133 -> about 50 lines; `Signatures` about 90 lines
shorter.

## Follow-ups

- `move` of a method should qualify the member types of the class it leaves that its body names.
