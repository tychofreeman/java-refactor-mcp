# Merge duplicate methods: a composition

- **Item:** roadmap #126 (gorefactor's `merge_duplicate_function`, parity section 3). The user's
  choice (2026-11-06): a documented composition, over a `merge` option on `fold` and over a new
  tool (CLAUDE.md's order: a composition, then an option, then a tool).
- **Date:** 2026-11-06
- **Branch:** merge-duplicates-recipe

## Plan

Two methods with the same body become one by two existing steps in one batch: `fold` of the
kept method (`Util.twice`) turns the duplicate's body into a call of it (and any other match),
then `inline` of the duplicate (`Util.doubled`) turns its calls into calls of the kept one and
deletes it. A batch golden pins the recipe; the descriptions of `fold` and `inline` name it.

## Notes

- The golden passed first time, but `inline` writes `twice((n + 1))`: an argument is
  parenthesized even where it lands as a call's argument, which needs none. Pinned as is; a
  follow-up.
- Limits, from the two tools: both methods static, bodies one return or one statement (`fold`'s
  and `inline`'s rules).

## Measured

Through jrefactor: the description edits; by hand the golden (resources) and this record.

## Assessment

No new code: the composition existed; the work was to show it and say it where a model looks.

## Follow-ups

- `inline`: no parentheses around an argument that lands as a call's argument or alone.
- Instance methods (`fold` and `inline` take static ones only).
