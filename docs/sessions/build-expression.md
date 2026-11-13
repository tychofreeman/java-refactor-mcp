# `build` on `replace_expression`

- **Item:** roadmap #165 (order 158), the generators sketch's increment 7: a call or a
  construction from names as an expression.
- **Date:** 2026-11-13
- **Branch:** build-expression

## Plan

`replace_expression` takes `build`, one object, in place of `new`. `Build.expression` reads it
with the same `generator` as a statement entry, refuses what is not a call or construction and
an `into`, drafts the expression in place (`drafted`: the file with it written, imports fixed,
parsed, the node found again at its offset), adds a diamond when the class is generic, and runs
`check` (the arity refusals). `Primitives.replaceExpression` finds the expression first, then
computes the replacement from `new` or `build`. `new` stops being required.

## Notes

- `read:` `Primitives.replaceExpression`, the `REPLACE_EXPRESSION` def, `ToolsTest` and
  `ServerTest`'s `build` checks.
- `hand:` the five golden cases (resource files), with the shell, as the brief allows.
- `plan:` `ToolsTest.aToolThatTakesBuildDoesNotRequireTheTypedText` pins the tools that take
  `build`; it now lists `replace_expression` too.
- Through the tools: one batch of five steps (two `add_declaration`, three `replace_text`),
  then one `replace_text` in `ToolsTest`.

## Measured

Not measured: the session-log hook is not loaded in this session.

## Assessment

Through the tools; no refusal, no large write. The plan held.

## Follow-ups

- None. The aliases (#166) are next.
