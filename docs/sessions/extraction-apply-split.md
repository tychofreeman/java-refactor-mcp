# Split Extraction.apply

- **Item:** roadmap order 34 (#41): `Extraction.apply` was 169 lines (classify,
  decide the return, build the signature, compose the text, compute the lines),
  and the `Selection.body` Javadoc said "innermost" where the code takes the
  outermost. Chosen with "Do them all in order" (the second of three).
- **Date:** 2026-11-03
- **Branch:** extraction-apply-split

## Plan

Three helpers beside `apply`, each one of its steps: `plan()` returns a `Plan`
record (the refusals, the variables used, the parameters, the results, the
returns, whether the method is void); `callOf(Plan, invocation)` returns a
`Call` record (the result type, the return line, the call line); `methodText`
renders the new method's text from them. `apply` keeps the edit composition and
the reply. Behavior is unchanged: the extract goldens are the test, and they
pass unchanged. The Javadoc is corrected to "outermost".

## Notes

- `read:` `Extraction.apply` (lines 87-256), once, to split it. `Extraction`
  outline with `inspect` first.
- `plan:` `plan()` throws `Exception` because `variables()` may; the record
  holds the same fields the old locals did.
- `bulk:` the three helpers and the new `apply` (about 100 lines) written in one
  batch: `add_declaration` after `apply` (the helpers), then `replace_declaration`
  of `apply`. This is new structure (the record and the three steps), not
  existing code typed again; the old body was replaced by a call to it.
- `hand:` none. The Javadoc fix was `replace_text`.

## Measured

Not measured: no session log for this branch.

## Assessment

Through the tools: the split was one batch of `add_declaration` and
`replace_declaration`, checked once at the end; no refusal. The reading was of
`apply` and the outline, and no other body. The plan held: the three steps
became the helpers, and `apply` now reads as the sequence. The file grew by
about 30 lines, because each helper has its own signature and Javadoc.

## Follow-ups

- `Extraction.apply` is now about 50 lines; the next step, if wanted, is the
  edit composition (`insertAt`, `edited`, `methodAt`) as its own helper.
