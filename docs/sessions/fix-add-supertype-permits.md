# add_supertype: a new clause goes before permits

- **Item:** issue #484 (code review of 2026-11-13, moves #18), group 2 of tracking issue #532
- **Date:** 2026-11-13
- **Branch:** fix-add-supertype-permits

## Plan

Reproduce with a golden case: `implements Named` on `sealed class Shape permits Circle`. Then in
`AddSupertype.run`, where a new clause starts, insert before `permits` when the type has one, not
before the brace.

## Notes

- plan: the golden case was refused, "wrote code that does not parse" (`permits Circle implements
  Named`), as the issue said.
- read: `inspect` with `pattern`, then lines 25-80 of `AddSupertype`.
- edit: two `replace_text` (the comment, the insertion point); `ASTNode` imported by the tool.
- The golden case's first draft had a package-private `name()`, which the gate rightly refused
  once the header parsed; made public.
- `MoveType.headerEnd` was not changed: the review found it safe today (it runs only when
  `permittedTypes()` is empty).
- hand: the golden files and the docs.

## Measured

Not measured: the session-log hook is not loaded here. All Java through the tools.

## Assessment

Through the tools.

## Follow-ups

None.
