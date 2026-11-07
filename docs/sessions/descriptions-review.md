# A technical writer's review of the tool descriptions

- **Item:** the user asked for a review of gorefactor's and jrefactor's descriptions "for
  unnecessary words and phrases, repetition, ambiguity, and other issues" (2026-11-07), then for
  the fixes. This repo's half; gorefactor's is its #604.
- **Date:** 2026-11-07
- **Branch:** descriptions-review

## Plan

Read the published text (`tools/list` against the jar), then fix descriptions and argument text:

- ": an edit," left in replace_text and replace_declaration, against the rule of 2026-11-06;
- paths: said once in the instructions ("Paths are relative to the project root"), not on 35
  `file` arguments and in inspect's description;
- extract_variable's `as` argument contradicted its description (a parameter may also use the
  method's parameters);
- extract_type's `struct` argument is Go's word, as its old name was: published as `new_type`,
  gorefactor's name for it, `struct` accepted (`Tools.ARG_ALIASES`).

## Notes

- read: a few lines of `Tools.java`, `Server.java` and the contract were read with grep and sed
  to place the edits, outside the read guard (a cloud session started outside the checkout);
  inspect would have served.
- The goldens' `struct=` became `new_type=` by sed (resources).

## Measured

No session log. All through `bin/jrefactor`: one batch of 15 replace_text steps on literals,
one on the stress test, three on docs; `spotless:apply`; `./mvnw verify` passed.

## Assessment

Text only, through the tools. The published list is shorter by the 35 path clauses.

## Follow-ups

- None here; gorefactor's record lists its own.
