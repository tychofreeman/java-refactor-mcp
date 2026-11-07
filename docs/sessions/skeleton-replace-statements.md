# Skeleton-only checks replace_statements in a member's body

- **Item:** the user's request (2026-11-07), from `ab-constructors.md` (#274): value-classes run 2
  typed three field-assigning constructors by `replace_statements` of their stubs' bodies, which
  skeleton-only did not check.
- **Date:** 2026-11-07
- **Branch:** skeleton-replace-statements

## Plan

When the replaced statements are a method's or constructor's whole body, parse the result, find
the member at the same offset, and run `Generated.checkMembers` on it, ending the refusal with the
`add_declaration` to send in place of the `replace_statements` (it replaces the stub, or since #275
a constructor that only assigns fields). One `insert_statements` in `Primitives.replaceStatements`
before the gate. Goldens: `skeleton_only_refused_stub_body` and `skeleton_only_allowed_stub_body`
(a body with logic).

A body's statements replaced in part are checked too, since the check is on the member as it
becomes, not on the statements sent: the block is the member's body whenever its parent is the
method.

## Notes

- hand: the goldens, resources, by a shell heredoc and sed.

## Measured

`bin/session-report`: 10 tool calls; through jrefactor 9 edit steps (docs included), 0 refusals;
by hand 2 (the goldens); large writes 0; denied or failed 1; reads: inspect 1.

## Assessment

- **Through the tools:** the Java change in one `insert_statements`; the goldens by hand.
- **Large writes:** none.
- **Did the plan hold?** Yes.
- **One change that would have helped:** none needed.

## Follow-ups

- `replace_text` and `replace_expression` that leave such a member are not checked; no run has used
  them for it.
