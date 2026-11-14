# One reader of a switch case's body (issue #517)

- **Item:** code-review issue #517 (group 4 of #532): `TypeSwitch.switchCases` and
  `EnumSwitch.arms` are near-copies, and `EnumSwitch` writes the call's placement twice.
- **Date:** 2026-11-14
- **Branch:** switch-arms

## Plan

Pure consolidation, so no behavior change and no red golden. `At.placeCall` by composition:
`extract_function` of the first copy, `remove_parameter` of `eFile` with `value=at.eFile()`,
`function_to_method` onto `At`, then `fold` for the second copy. `TypeSwitch.armBody(statements, i,
value, blockOrThrow)` by `add_declaration` and two `replace_text`s of the copies. A unit test for
`armBody`; every `type_switch*` golden and probe passes unchanged.

## Notes

- read: both readers by `inspect` `names`; the two placement blocks and `At` by line range.
- extract_function of the first copy: applied.
- refusal: `remove_parameter` with `value=at.eFile()` (the call passes the local `eFile`); its fix,
  `replace_expression` of the argument, went first in a batch.
- refusal: `function_to_method` (first parameter not of the class), then `move` to `EnumSwitch.At`
  (a member record, not a top-level class). hand-ish: the move as `add_declaration` to `At`,
  `replace_text` of the call, `replace_declaration` deleting the static method, in one batch with
  the two steps before (the first try put the delete before the call's rewrite and was refused).
- refusal: `fold` of `At.placeCall` (it returns a value from statements); the second copy by
  `replace_text`.
- bulk: `armBody` and its two callers in one batch of 3; imports fixed by the tool.
- create_file of `TypeSwitchTest`; spotless after. Friction log: the three refusals.
- hand: docs only.

## Measured

Not measured (no session-log hook here). From the notes: 8 tool calls for the Java, 4 of them
refused (none changed anything); no Java typed outside the tools, but the method's move to `At` was
written as an edit where a refactoring should have carried it.

## Assessment

Through the tools, with a detour: the plan's composition failed at the move into a nested record
and at the fold of a value-returning method; both are logged.

## Follow-ups

- `move` (and `function_to_method`) to a member type or a record.
- `fold` of a method whose statements build a local and return it.
