# replace_statements: content, build or surround as one precondition (#528, item 4)

- **Item:** issue #528, item 4 of its last comment: `Primitives.replaceStatements` checked the
  exclusivity of `content`, `build` and `surround` in four places.
- **Date:** 2026-11-15
- **Branch:** statements-choice

## Plan

Pin the three refusals no golden covered (content and surround, build and surround, none), then
one `Precondition.require` at the top of the method with the four checks in their old wording,
and delete the four scattered `if`s.

## Notes

- golden: `replace_statements_refused_content_and_surround`, `_build_and_surround`, `_nothing`;
  they pass on main and after the change.
- edit: one batch, `insert_statements` of the precondition and four `replace_statements` with
  empty content. No refactoring moves a guard out of the code it sits in (friction log).

## Measured

Not measured (no session-log hook). 1 batch of 5 edit steps; no Java by hand.

## Assessment

Through the edit tools, not refactorings. The checks now run before the file is read and before
`build` runs, so a call with two conflicting arguments and another mistake is refused for the
conflict first; the refusal texts are unchanged.

## Follow-ups

None.
