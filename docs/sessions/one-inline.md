# One inline

- **Item:** roadmap #122, gorefactor's `docs/parity.md` section 2 (the user, 2026-11-05: one
  `inline`, by the same reasoning as one `move`; gorefactor #571 built theirs).
- **Date:** 2026-11-06
- **Branch:** one-inline

## Plan

`inline` is published in place of `inline_function` and `inline_variable` and routes by its
arguments: `line` with `name` inlines the local variable named there (`inline_variable`, with
`column`); `name` as `Type.method` without `line` inlines every call of that static method and
deletes it (`inline_function`). A bare name without `line` is refused, naming the two forms. The
two old names stay aliases; refusals and descriptions that name them name `inline`. Inline class
(gorefactor's `field` route) is #128, to be built inside it later.

Derivation: goldens first (one per route, and the refusal); the `Def` and router beside `MOVE`'s
(`add_declaration`); `ALL` and `ALIASES`; the texts that name the old tools.

## Notes

- read: the two `Def`s' arguments; gorefactor's `refactorings/inline/inline.go` (its routes).
- One batch for the `Def`, router, `ALL`, `ALIASES`; every test passed; then the refusal texts
  (`InlineFunction` 4, `InlineVariable` 2, two in `Tools`), the README and both instruction files.

## Measured

Through jrefactor: 3 edit batches and the docs; no refusals; by hand the goldens (resources, by
`cp`), one roadmap cell by script, this record.

## Assessment

Through the tools, as planned; the record came first this time.

## Follow-ups

- Inline class inside `inline` (#128); a per-call inline of a function (Go's `line` route).
- The A/B check of the published list, with #121's (ask the user).
