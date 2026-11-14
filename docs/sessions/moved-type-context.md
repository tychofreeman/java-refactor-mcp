# MoveType's context as one record (#528, part 1)

- **Item:** issue #528 (code review, group 5 of #532), the moves finding: `MoveType.qualifyAndWiden`
  (12 parameters) and `MoveType.permits` (13) pass the same tuple, and `x >= start && x < end` is
  repeated.
- **Date:** 2026-11-14
- **Branch:** moved-type-context

## Plan

1. `extract_type` of `permits`' eight adjacent parameters (`start` .. `outerLabel`) into a record
   `MovedType`, held as `moved`.
2. One `MovedType nested` in `run`, the call passing it.
3. `qualifyAndWiden`, `identifiers`, `label` and `movedName` take it: `add_parameter`, then
   `remove_parameter` with `value=moved.x()` for each field it carries, then `inline` of the locals
   that writes.
4. `MovedType.inside(offset)` in place of each range test (`replace_expression`).
5. `label` and `movedName` become `MovedType`'s instance methods.

## Notes

- extract_type: `MoveType.permits` into `MovedType` (written `public record`, one line; rewritten
  with a doc comment, package-private, and `inside`).
- hand: the `new MovedType(...)` the call got, hoisted out of the loop (`insert_statements` after
  `end`, `replace_expression` of the argument): no tool hoists a loop-invariant local. The local is
  `nested`, since `run` has a `moved` (the moved text).
- refusal: `remove_parameter` `key` with `value=moved.key()`: the call passed the local `key`, not
  `nested.key()`; followed its fix (`replace_expression` of the four arguments first), in a batch.
- add_parameter/remove_parameter (value)/inline: `qualifyAndWiden` (4 parameters), `label` (4),
  `movedName` (3); `identifiers` lost `start`, `end` (plain `remove_parameter` once its body used
  `moved.inside`); `move_parameter` to put `moved` where it reads well.
- replace_expression: 9 range tests to `inside`. replace_statements: `qualifyAndWiden`'s unused
  `start`/`end` locals.
- refusal: `move` of `MoveType.label` to `MovedType`: a record is not a class `move` takes
  (`Opened.topLevel`). hand: `add_declaration` of the instance methods, `replace_text` of the calls,
  `replace_declaration` deleting the statics, `MoveType.declaredBy` widened.

## Measured

Not measured (no session-log hook here). Refactorings: 1 `extract_type`, 4 `add_parameter`,
13 `remove_parameter`, 11 `inline`, 4 `move_parameter`; edits: 9 `replace_expression`, 1
`insert_statements`, 1 `replace_statements`, 1 `add_declaration`, 1 `replace_declaration` (record),
2 `replace_declaration` (deletes), 4 `replace_text`.

## Assessment

Through the tools. Two gaps forced edits: no hoist out of a loop, and no `move` of a static method
onto a record (then `function_to_method`). The exceptional-behavior notes `remove_parameter` and
`inline` gave (`moved.key()` "may throw") are for a record's accessor on a parameter never null; no
behavior change. Every `move*` golden passes unchanged.

## Follow-ups

- `move` to a record or a member type (also asked by the switch-arms entry of the friction log).
- `MoveMember.rewrite` (10 parameters) and `EncapsulateField.useEdit` (8): listed on #528.
