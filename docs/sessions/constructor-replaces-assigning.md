# A constructor replaces one that only assigns fields

- **Item:** the user's request (2026-11-07), from `ab-constructors.md` (#274): after #272, final
  fields and then `constructor` in one batch gave a duplicate constructor.
- **Date:** 2026-11-07
- **Branch:** constructor-replaces-assigning

## Plan

`add_declaration` already replaces a stub of the same signature (`Declarations.stubFor`). A
constructor that only assigns its parameters to the fields of their names
(`Generated.onlyAssignsParameters`) carries nothing a new constructor of the same parameter types
would lose, so `stubFor` takes it too, and the result says "replaced the constructor ..., which only
set fields". Goldens first: `add_declaration_constructor_replaces_assigning` (by `constructor`),
`_typed` (content with logic), and `batch_fields_then_constructor`, the A/B's sequence.

## Notes

- hand: the goldens, resources, by a shell heredoc.
- plan: checked that the three goldens fail without the fix (a stash of `src/main`): each with
  "Duplicate method".
- read: searched for `add_declaration`'s description by its contract wording, which #256-#259 had
  shortened; found it by the tool's name.

## Measured

`bin/session-report`: 28 tool calls; through jrefactor 10 edit steps (docs included), 0 refusals;
by hand 2 (the goldens); large writes 2 (the goldens' heredocs, fixture Java in resources); denied
or failed 1; reads: inspect 10, six of them looking for the description's wording.

## Assessment

- **Through the tools:** the Java change and the description in two batches; the goldens by hand,
  as resources.
- **Large writes:** none.
- **Did the plan hold?** Yes.
- **One change that would have helped:** none needed.

## Follow-ups

- Rerun value-classes to confirm the duplicate is gone (part of the next A/B).
- `replace_statements` of a stub body passing skeleton-only: its own PR.
