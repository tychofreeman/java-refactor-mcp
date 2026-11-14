---
order: 366
item: 376
status: Done
---

## Item

Indentation was worked out by about a dozen helpers with differing rules (issue #499, code review), and `move` (`pull_up`, `push_down`, any `MemberMove.addTo`) of a member whose Javadoc has two or more lines took the comment's one-space star offset for the old step, so each step of the body came out at twice the target file's step (a session's `move` into a near-empty class, 4 spaces a step in a 2-space file)

## Status

Done 2026-11-14: one `Indent`: `width` (a line's leading spaces and tabs), `step` and `step(text, fallback)`, `reindent` (common indentation replaced; was `LoopToStream.reindent`, and `Primitives.indent` delegates to it, since `inline` refused it), `restep` (was `MemberMove.reindented`; a comment's continuation line is a step plus one, and sets no step) and `member` (was `MemberMove.memberIndent` and `Declarations.memberIndent`). Where the copies differed, the rule kept is the one no golden noticed: the step fallback is 2 everywhere (`LoopToStream` and `ConvertLoop` had 4), `member` looks past a first member on the brace's line (`MemberMove`'s rule; `Declarations` took the first member only), and the `Math.max(step, 2)` sites keep a one-space step. Also fixed: `subclass_to_strategy` resteps the moved body to the base's step (it shifted the member indent only), and `add_result` of an empty void method indents its `return` by the file's step (was two spaces). Goldens `move_member_javadoc_step`, `subclass_to_strategy_restep`, `add_result_void_empty_step`; `IndentTest`. Left open: a tab step (`step` counts a tab as one space), `ConvertLambda`'s and `TypeSwitch`'s written two-space steps, `Primitives.step`'s two-space fallback, `MoveType.dedent`, `IndentShift.shift` (spaces only), and a text block inside a body `restep` moves

## Evidence

Issue #499; `docs/sessions/indent-utility.md`
