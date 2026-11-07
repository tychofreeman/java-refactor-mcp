# The blank-final refusal names constructor

- **Item:** the user's request (2026-11-06), from the friction-log entry of
  `ab-library-skeleton.md`: both reservation runs added `final` fields before their constructor,
  were refused for "The blank final field book may not have been initialized", and found the
  batch themselves.
- **Date:** 2026-11-06
- **Branch:** final-fields-constructor

## Plan

A compile refusal whose errors are blank final fields says how to write their constructor:
`add_declaration` with `constructor: [the fields]` in the same batch. One place: `Gate.refusal`,
which words every compile refusal from the error lines. Golden first
(`add_declaration_final_fields_refused`), then `replace_declaration` of `Gate.refusal`: the field
names from those lines, a sentence naming `constructor`, which takes the place of the edit's own
fix when every error is one of them.

## Notes

- bulk: `replace_declaration` of `Gate.refusal`, 42 lines: the method's 16 existing lines written
  again with the new ones between them. An `insert_statements` before the `return` and a
  `replace_expression` of `fix` would have kept the old lines untouched; one replacement was
  quicker to plan from one read.

## Measured

`bin/session-report`: 12 tool calls; through jrefactor 1 edit step, 0 refusals; by hand 2 (the
golden, resources, by a shell command; this record); large writes 1 (`Gate.refusal`, 42 lines);
reads: inspect 2.

## Assessment

- **Through the tools:** the one Java change through `replace_declaration`, which also chose
  `java.util.regex.Pattern` over JDT's by the package's imports and said so.
- **Large writes:** new behavior, plus the method's existing lines typed again (see the note);
  a composition of `insert_statements` and `replace_expression` fitted.
- **Did the plan hold?** Yes, as written.
- **Reading:** `Gate` lines 40-110 to find where refusals are worded; the pattern search found
  that every compile refusal goes through `Gate.refusal`.
- **One change that would have helped:** none needed.

## Follow-ups

- A run of the reservation task would show whether the refusal saves the batch round trip.
