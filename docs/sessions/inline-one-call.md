# inline of one call

- **Item:** issue #368 (parity with gorefactor's `inline`), roadmap #502
- **Date:** 2026-11-15
- **Branch:** inline-one-call

## Plan

`inline` with `line` at a call inlines that call. Derivation: `InlineFunction.run` already
inlines every call site it finds; give it the one call to keep (a record `Call`, added as a
parameter with `add_parameter` so the existing caller passes null), count what it skips, and
delete the method only when nothing is left. A new entry, `atCall`, turns `line` and `name` into
the declaring file and `Type.method`; `Tools.inline` routes to it before the variable route.

## Notes

- plan: goldens first (`inline_call_one`, `_last`, `_bare_name`, `_keep_function`,
  `inline_all_calls_keep`, `inline_call_refused_no_call`): five errors, one wrong refusal.
- read: `inspect` of `InlineFunction` (outline, then `run`), `ByLine.named`, `InlineVariable.variableAt`.
- bulk: `add_declaration` of `Call`, then two `add_parameter` steps in one batch.
- refusal: a batch's `Tools.inline` rewrite left `target` unresolved; `retry` with one more step fixed it.
- refusal: `replace_statements` named a duplicate local `method`; renamed in the content.
- plan: the golden `inline_record_accessor` (`List<String> b = p.b();`, name `b`) had read the
  bare name as the variable; kept that (a default, in `docs/design.md`).
- hand: none in Java; shell quoting of a batch with apostrophes needed the JSON in a file (`@file`).

## Measured

Not measured (no session-log hook here). Java changes: 8 tool calls (2 batches of 3-5 steps, 1
retry, `add_declaration` x2, `replace_text` x2, `replace_statements`), none by hand.

## Assessment

Through the tools. The plan held; Holding's paths and `Opened`'s did not compare as `Path`s
in the first try, so the record keeps the relative path.

## Follow-ups

- An override of a supertype method is deleted when its last direct call is inlined, as the
  every-call route does; a call through the supertype still reaches it. Not reproduced yet.
