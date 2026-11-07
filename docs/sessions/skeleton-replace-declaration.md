# Skeleton-only checks on replace_declaration of a type

- **Item:** the user's request (2026-11-07), from the friction-log entry of `ab-hints.md` (#269):
  under skeleton-only, a model refused a typed constructor sent the whole `Reservation` class,
  constructor included, through `replace_declaration`, which the rule did not check.
- **Date:** 2026-11-07
- **Branch:** skeleton-replace-declaration

## Plan

`replace_declaration` of a type runs the skeleton member checks (`Generated.checkMembers`) on the
new type's members, as `add_declaration` does on its content. The refusal's last words differ: not
"in place of content" but the `add_declaration` to send after the replacement, in one batch, the
member left out of the type. Goldens first (`skeleton_only_refused_replace_type`, and
`skeleton_only_allowed_replace_type` for a type with only fields and a getter), then:
`add_parameter` `instead` to `checkMembers` (the old text as its value), `replace_text` of the two
endings to use it, and `insert_statements` of the check in `Declarations.replace`.

The members checked are all of the new type's, not only those the old type lacked: a member typed
again is what the rule is about either way, and a diff of old and new members is more code for a
case no run has shown.

## Notes

- hand: the two goldens, resources, by a shell heredoc.
- plan: `checkMembers` took `List<BodyDeclaration>`; a type's `bodyDeclarations()` is a raw list, so
  `change_type` made it `List<?>` and the loop `Object`, the codebase's idiom, rather than an
  unchecked conversion.

## Measured

`bin/session-report`: through jrefactor 2 refactoring steps, 12 edit steps (the docs included);
0 refusals; by hand 2 (the goldens, and their expected refusal rewritten); large writes 1 (the
goldens' heredoc, 48 lines of fixture Java, resources); reads: inspect 7.

## Assessment

- **Through the tools:** every Java change was a refactoring or an edit: `add_parameter` and
  `change_type` carried the signature to the calls; the new behavior was one 11-line insert.
- **Did the plan hold?** Yes, with `change_type` added for the raw list.
- **Reading:** `Declarations.replace` and `Generated.checkMembers`, to place the check and reuse it.
- **One change that would have helped:** none needed.

## Follow-ups

- Nested types in the replacement are not checked, nor `replace_text` that writes a constructor;
  no run has used either.
