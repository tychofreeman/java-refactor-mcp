# parameterize

- **Item:** issue #372, the owner's option (B): a published `parameterize`; roadmap #508
- **Date:** 2026-11-15
- **Branch:** parameterize-tool

## Plan

gorefactor's `internal/refactorings/parameterize` in Java: find each method, check one type and
one signature, match the first body against each other one with an `ASTMatcher` that records
pairs of literals, take the literals whose text differs, write the new method from the first's
text with those literals replaced, and each body as one call. Goldens and a probe first.

## Notes

- read: gorefactor's `parameterize.go` and its six golden cases (arguments, refusal wording).
- plan: goldens `parameterize_constants`, `parameterize_methods`, four `parameterize_refused_*`,
  probe `parameterize_values` (five constants of four types); red.
- bulk: `create_file` of `Parameterize`; one batch of three in `Tools` (definition, `ALL`, handler).
- refusal: none from the tools. Two fixes after the first run, one batch: parameter names checked
  against variables only (`String.format` had refused `format`), and the place of a difference
  lifted past a name only into the expression it names (`x + 1` had been shown for `x`).
- hand: goldens and probe written as resources.

## Measured

Not measured (no session-log hook). Java changes: 3 tool calls (`create_file`, two batches), none
by hand.

## Assessment

Through the tools.

## Follow-ups

- gorefactor's `docs/parity.md` should mark `parameterize` done for Java (its repo, its own PR).
