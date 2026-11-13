# Under skeleton-only, typed statements that `build` writes are refused

- **Item:** roadmap #167 (order 160), the generators sketch's increment 9 with the mechanism the
  user chose for question 2: a refusal under `JREFACTOR_SKELETON_ONLY` naming the `build` to send.
- **Date:** 2026-11-13
- **Branch:** skeleton-build-refusal

## Plan

`Generated.checkBuild(content)`, syntax only, as `checkSurround` is: content that parses as one
statement of a shape `build` writes is refused, with the entry to send written out as JSON
(Jackson, so the strings in it are escaped). Called from `insert_statements` and
`replace_statements` when `content` (not `build`) was sent, beside `checkChanged` and
`checkSurround`. Then the refactor: the one-statement parse both checks do, as `only`.

## Notes

- `read:` `Generated`, `Primitives.insertStatements` and `replaceStatements` through `inspect`.
- `hand:` the seven golden cases (resource files), with the shell, as the brief allows.
- `refusal:` the first batch was refused: two pattern variables named `p` and `n` in one
  condition (`counted`). Renamed and resent.
- Through the tools: one batch of four steps (two `add_declaration`, two `replace_text`), then
  the refactor as one batch of four.
- `plan:` which statements count was a choice the sketch left open: only those where `build`
  reads a type from the code (declared locals from a call or construction, for-each) and the
  index loop; recorded in `docs/design.md` as a default to reopen.

## Measured

Not measured: the session-log hook is not loaded in this session.

## Assessment

Through the tools; the plan held. The refactor step (one helper for the parse both checks
repeat) came after green, as CLAUDE.md orders it.

## Follow-ups

- Whether the refusal steers models to `build` is the A/B measurement of the sketch's section 8;
  not run (issue #376).
