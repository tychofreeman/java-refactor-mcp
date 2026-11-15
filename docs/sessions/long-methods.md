# TypeSwitch.run and ConvertLambda.run in parts (#528, item 8, part 2)

- **Item:** issue #528, item 8 of its last comment, the control-flow long methods:
  `TypeSwitch.run` (162 lines) and `ConvertLambda.run` (83).
- **Date:** 2026-11-15
- **Branch:** long-methods

## Plan

`extract_function` of the parts that take few of `run`'s locals: ConvertLambda's switch on the
target form; TypeSwitch's call text and the texts of the files it writes.

## Notes

- extract_function: `ConvertLambda.replacement` (4 parameters), `TypeSwitch.call` (4),
  `TypeSwitch.texts` (5).
- Not extracted: TypeSwitch's loop over the cases (9 parameters) and its supertype method's text
  (6); `ConvertLoop.run` (84 lines, a dispatch whose last branch only throws: extracting the
  refusal leaves `said` unassigned on that path, and the whole dispatch takes 6). `EnumSwitch.method`
  shares its constant-body loop with `field` (review finding 19), a separate change.

## Measured

Not measured (no session-log hook). 3 `extract_function`; no Java by hand.

## Assessment

Through refactorings. `TypeSwitch.run` 162 -> about 145 lines; `ConvertLambda.run` 83 -> about
60.

## Follow-ups

- The rest of item 8: `ExtractType.fields`/`components`, `Behavior.checkCrossed`/`checkExits`,
  `EnumSwitch.method`, `ConvertLoop.run`, TypeSwitch's case loop.
