# One rule for jumps that leave code (issue #515)

- **Item:** code-review issue #515 (group 4 of #532): the leaving-jump analysis is duplicated, and
  `TypeSwitch`'s copy is weaker than `Effects`'; the last-statement-is-a-jump test is written three
  more times.
- **Date:** 2026-11-14
- **Branch:** leaving-jumps

## Plan

Two questions, one place each, in `Effects`: does this jump leave this code (`exits`, from
`Effects.leaves`' `lands`, generalised to any root), and can this statement complete normally
(`completesNormally`). Then `TypeSwitch.usesOnly`, `RemoveElse` and `ConvertLoop` call them and
their copies are deleted. Red first: a labeled break and a local class's return in a
`type_switch_to_method` case, a `for` ending in `yield` for `convert_loop`, and `remove_else` with a
then-branch ending in an if/else.

## Notes

- read: the six sites by `inspect` `names`, `RemoveElse.run` and the two `ConvertLoop` uses by line
  range.
- red: all four goldens failed: the gate's compile refusal for `break outer`, the tool's refusal of
  a local class's `return`, the gate's refusal of `i++` after `yield`, `remove_else`'s "can finish
  normally".
- bulk: one `batch` of 11 steps (add_declaration, replace_text, replace_declaration with empty
  content to delete the three copies); imports fixed by the tool.
- plan: `MoveStatement.isJump` asks whether the moved statement itself is a jump, and
  `Extraction.terminates` whether every path returns or throws (a break there does not return a
  value): both left with their own rules.
- out: the three applied goldens' `out/` are the tool's output on a copy of `in/` (from the
  rebuilt jar), read and checked.
- add_declaration of the `EffectsTest` cases; spotless after.
- hand: docs only.

## Measured

Not measured (no session-log hook here). From the notes: 2 tool calls wrote all the Java (one batch
of 11 steps, one add_declaration).

## Assessment

Through the tools; the plan held. `remove_else` gains an accepted shape (a then-branch ending in an
if/else that cannot complete), a default recorded in the roadmap row.

## Follow-ups

- `TypeSwitch.usesOnly`'s refusal still prints the jump by `toString()`; `Nodes.show` (#526) fits.
