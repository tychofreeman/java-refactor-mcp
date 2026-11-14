# remove_else: the else's locals in the enclosing scope (issue #471)

- **Item:** issue #471 from the 2026-11-13 code review (roadmap row 220, order 212)
- **Date:** 2026-11-13
- **Branch:** fix-remove-else-scope

## Plan

Removing the else moves the else block's declarations into the enclosing block. Refuse when a
statement after the if names one of them (as `ConvertLoop.namedAfter` does), with `rename` as the
fix. The issue's aside, tab indentation: `leadingSpaces` counts tabs too, renamed `indentWidth`.

## Notes

- read: `RemoveElse.run` and `elseStatements` by `inspect` ranges; `ConvertLoop.namedAfter` by `names`.
- red: probe `remove_else_local_hides_field` printed "after local" in place of "after field";
  goldens `remove_else_refused_local_named_after` (applied) and `remove_else_tabs` (dedented by 2
  characters too few) failed.
- refusal: a `rename` step inside the batch needs `line`; dropped by `retry`, run after.

## Measured

Not measured: the session-log hook is not loaded here.

## Assessment

Through the tools. The plan held.

## Follow-ups

- The check is by identifier, so `p.name` after the if is refused too; by binding would be exact.
