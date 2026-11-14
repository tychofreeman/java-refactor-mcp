# Node source text through Nodes.source

- **Item:** issue #507 (code review, group 4 of #532): eleven private `source()` helpers and about
  47 inline substrings.
- **Date:** 2026-11-14
- **Branch:** node-text

## Plan

`move` one helper into `Nodes`, `fold` `Nodes.end` so every `getStartPosition() + getLength()`
becomes `Nodes.end(n)`, then `fold` the moved helper so every substring of a node and every other
helper's body becomes its call, then `inline` each helper. Pure consolidation: goldens and probes
are the check, a unit test pins the helper.

## Notes

- read: `inspect` `names` of the twelve helpers at once; all the same rule (two spelled with
  `getStartPosition() + getLength()`, the rest with `Nodes.end`).
- refusal: `rename` to `text` (capture by the parameter `text`); kept the name `source`.
- bulk: `fold` `Nodes.end` (44 sites, ten left), `fold` `Nodes.source` (46 sites, two left).
- bulk: eleven `inline` calls, one per helper.
- Spotless re-wrapped lines the inlines made longer.

## Measured

Not measured (no session-log hook); calls are in `JREFACTOR_LOG`. Java changes in 15 tool calls
(`move`, two `fold`, eleven `inline`, `add_declaration` of the test), none by hand.

## Assessment

Through the tools, and cheaply: two folds did what the review counted as about 90 sites.

## Follow-ups

- `fold` with getters called twice (ten sites left); `Lines.skipSpace` and
  `Edits.dropModifier` from the review's moves #13.