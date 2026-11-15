---
order: 490
item: 500
status: Done
---

## Item

`inline` of one call: a method inlined at the call on `line` (and `column`), the method kept while
anything else uses it; `keep_function` and `all_calls` as gorefactor's (issue #368, parity review of
2026-11-13).

## Status

Done 2026-11-15: `InlineFunction.atCall` finds the call of `name` on the line and runs
`InlineFunction.run` with that one call (`Call`); `Tools.inline` routes by what `name` is there.
The method is deleted when no other call or method reference is left and `keep_function` is not
given; `Type.method` without `line` still inlines every call. Goldens `inline_call_*`,
`inline_all_calls_keep`; probe `inline_one_call`. Decisions in `docs/design.md`.

## Evidence

gorefactor's `inline` takes one call at `line` and `all_calls` for every one; a Java recipe that
inlines one call was refused here ("no variable named ..."). The parity review of 2026-11-13
(go-refactor-mcp `docs/parity-review-2026-11-13.md`, section 2) lists it first of the gaps.
