# invert_if: dangling else (issue #470)

- **Item:** issue #470 from the 2026-11-13 code review (roadmap row 211, order 203)
- **Date:** 2026-11-13
- **Branch:** fix-invert-if-dangling-else

## Plan

The else branch becomes the then branch; when it is unbraced and its last nested statement is an
if without an else, the new `else` would attach to that if. Wrap it in braces there (one helper
that follows loop bodies, labels and else branches), leave other branches as they were.

## Notes

- read: `InvertIf.run` by `inspect` pattern; the issue named the lines.
- red: probe `invert_if_dangling_else` printed 100 then 102 (the old then branch ran per element);
  golden `invert_if_dangling_else` differed.
- hand: a first version by a Python script before the red run; reverted (friction log).
- plan: one `batch`: `replace_text` of the call, `add_declaration` of `thenText` and `endsInOpenIf`;
  imports added by the tool. Then `replace_text` for visibility and the class comment.

## Measured

Not measured: the session-log hook is not loaded here.

## Assessment

Through the tools after the revert. The plan held.

## Follow-ups

None.
