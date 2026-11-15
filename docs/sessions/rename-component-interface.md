# rename of a record component whose accessor implements an interface method

- **Item:** roadmap item 519, issue #674 (the owner's list of 2026-11-15, third item; option (a))
- **Date:** 2026-11-15
- **Branch:** rename-component-interface

## Plan

At the level of the change: the accessor is one member of a method family, and `rename` of a
method already renames a family (`MethodFamily.family`, `refuseMethodClash`). So the component's
rename takes the accessor's family as one more set of uses. A generated accessor has no
`MethodDeclaration`, so the family walk cannot start from it: start it from each method the
accessor overrides (`Hierarchy.overridden`). Refactor first (green): the `Holding` the rename
reads, named and moved before `isUse`, so the family can be read from its units. Then the goldens
red, then: a helper (`accessorFamily`), the family's names as uses, the clash check per file, the
result naming the other methods.

## Notes

- read: `inspect` of `Rename` (outline, `renameMethod`, `renameComponent`, `componentField`,
  `checkMeanings`, `refuseMethodClash`), `MethodFamily.family`, `Hierarchy.overridden`.
- `extract_variable` of `Holding.of(project, oldName)`; `move_statement` up twice.
- refusal: `move_statement` (both statements call): `names_only=true`, right, as they share no
  written name; then step 2 named a line step 1 rewrote: `$1.start`. The result reported the
  exceptional change (a throwing read moved before another), accepted.
- red: the interface goldens errored (the gate refused the record no longer implementing `HasX`);
  the clash and library goldens got the gate's refusal, not theirs.
- refusal: insert_statements' line in a batch: the line was inside a `for`, not a statement start;
  retried with the `for`'s line.
- refusal: `replace_expression` takes `expression`, not `old`; retried.
- green: `add_declaration` (`accessorFamily`) and three `insert_statements` in one batch; then
  `replace_statements` (the label only for other types) and `replace_expression` (the result).
- Default to reopen: an accessor that implements a library method is refused (the family cannot be
  renamed), as `MethodFamily.family` refuses a method that overrides one.

## Measured

Not measured: the session-log hook is not loaded here. Java written through the tools: 7 calls
(batches included), 0 hand edits; formatting by `spotless:apply`.

## Assessment

Through the tools. Three refusals, each saying what to send; `$N.start` and `retry` kept them
to one resend each.

## Follow-ups

None.
