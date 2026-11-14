# One Indent utility

- **Item:** issue #499 (code review core #5, signatures #11, moves #14, control-flow #9 and #14),
  roadmap order 366 (#376); and the `move` into a near-empty class that wrote 4 spaces a step
  (friction log, 2026-11-14, hierarchy-helper)
- **Date:** 2026-11-14
- **Branch:** indent-utility

## Plan

Make a class `Indent` the home of indentation, then fix the reported bug in one place. Operations:
`create_file` of `Indent`; `move` of `Declarations.step`, `MemberMove.reindented` and
`LoopToStream.reindent` into it; `fold` of the inline width expression; each private copy made a
delegate (`replace_declaration`) and inlined; the step fallbacks as `replace_text` in one batch.
Then a golden for the bug (red) and the fix inside `restep`.

## Notes

- read: the bug did not reproduce with a body alone; it needs a Javadoc of two or more lines,
  whose ` * ` lines are one space past the member and set `reindented`'s old step to 1. Not a
  near-empty target: the target's step was right.
- refusal: `section @@prim is not used` (the JSON had `"@@prim"`; a section is `"@prim"`).
- refusal: `rename needs line`; fixed by `retry`.
- refusal: `inline` of `Primitives.indent` (an effect of `stripTrailing()`); step removed by
  `retry`, the delegate kept.
- plan: defaults picked by running every golden with one rule (fallback 2, `MemberMove`'s member
  rule): 858 passed unchanged.
- read: `InlineFunction.reindent` already keeps text blocks (#222); the review's concern there
  was out of date.
- hand: none in Java. Golden and test resources written as files.

## Measured

Not measured (the session-log hook is not loaded); CLI calls are logged to `JREFACTOR_LOG`.
Counts from the notes: 13 tool calls that wrote (5 batches, 30 steps), 0 Java edits by hand.

## Assessment

Through the tools. `move` into the new class showed the bug on itself. The plan held; `inline`
refusing a `String` call cost one delegate left in `Primitives`.

## Follow-ups

The roadmap row's "Left open": tab steps, `ConvertLambda`'s and `TypeSwitch`'s written steps,
`Primitives.step`, `MoveType.dedent`, `IndentShift.shift`, text blocks under `restep`.
