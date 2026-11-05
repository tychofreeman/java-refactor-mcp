# inline_function takes bodies of several statements (roadmap #84, second part)

- **Item:** roadmap #84, widening existing tools: `inline_function` for a body
  of several statements (it took one `return` or one statement).
- **Date:** 2026-11-04
- **Branch:** inline-function-statements

## Plan

- A void method: its call statement becomes the body's statements,
  re-indented to the call's.
- A method ending in its one `return expr`: the call must be all of an
  initializer (`T v = m(..);`), a `return`, or an assignment's value, directly
  in a block, so that the body's other statements can go before that
  statement without running ahead of anything it runs first; the call
  becomes `expr`.
- Refused: a `return` before the last statement; a body that assigns a
  parameter (it would assign the caller's argument); a body local whose name
  the calling member uses (they would clash, or one would hide the other).
- The argument checks of #67 (effects before a use, deferred uses, order)
  run over the whole body.

1. Red: goldens `inline_function_statements_void`, `_value`, `_refused_clash`,
   `_refused_assigns`, `_refused_part`.
2. Green: `replace_text` of the body test in `run`, `replace_declaration` of
   `replacementFor` (it returns a list of edits now), `add_declaration` of the
   helpers.

## Notes

- `bulk:` `replacementFor` written again by `replace_declaration` (about 75
  lines): most of it is the old body with the prefix case added; a series of
  `replace_text` edits would have been as long, so it was written out.
- `refusal:` `replace_text` of the parentheses check: "old is not in" (the
  text was indented two more spaces than I wrote, after the
  `replace_declaration` re-indented it); then "Duplicate local variable
  whole" (a parameter of that name); sent a third time as `entire`.
- The value golden first gave `int t = (sum * 2);`: a call that is all of an
  initializer, return or assigned value needs no parentheses, so they are
  now left off there. No other golden changed.

## Measured

From `bin/session-report` (before the docs edits): 17 tool calls; 25 edit
steps through jrefactor; 2 refusals (above), both answered by another
`replace_text`; no Java by hand; 2 large writes (`replacementFor` and the
helpers).

## Assessment

- **Through the tools or around them:** through.
- **Large writes:** `replacementFor` is existing code written again with the
  new case; noted above.
- **Did the plan hold?** Yes.
- **One change that would have helped:** none.

## Follow-ups

- Instance methods (the next part of #84).
- A body local whose name only a sibling member uses is refused too; the
  check looks at the whole calling member, not its scopes.
