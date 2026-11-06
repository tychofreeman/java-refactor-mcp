# Small fixes: after by overload label; inline's parentheses

- **Item:** the third of the user's "do all 4" (2026-11-06): the friction-log entry of 2026-11-06
  (`after` refused `addParameter(SourceProject, Args)`, a label its own refusal listed) and the
  follow-up of #126 (`inline` wrote `twice((n + 1))`).
- **Date:** 2026-11-06
- **Branch:** small-fixes

## Plan

1. `Declarations.member` compares a member's label with the name given, both without spaces:
   today it strips the given name only, so a label with two parameters never matches.
2. `inline` (of a method) leaves an argument bare where it lands as a whole argument of a call,
   a whole return value, or a whole initializer: there no operator can bind into it.

Goldens first for each; then the one-line comparison, and the parenthesis rule where
`InlineFunction` wraps an argument.

## Notes

- read: `Declarations.member` (the cause: `label(d)` kept the space after each comma, the given
  name was stripped), `InlineFunction.substituted` and `ParamUse`.
- Goldens red first (`add_declaration_after_overload`; `batch_merge_duplicate_methods` now
  expects `twice(n + 1)`); one edit and one batch of four; every test and probe passed.

## Measured

Through jrefactor: one `replace_text`, one batch, the docs; no refusals; by hand the golden and
one expected line (resources) and this record.

## Assessment

Through the tools; both small, as planned.

## Follow-ups

None.
