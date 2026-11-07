# An edit that leaves final fields unset writes their constructor

- **Item:** the user's request (2026-11-07), option 1 of three offered after #271: "Why should the
  model have to generate that code at all?" With the record hint. Option 2 (a stub constructor
  that assigns fields) follows in its own PR.
- **Date:** 2026-11-07
- **Branch:** fields-make-constructor

## Plan

Every single-file edit passes `Edits.gated`, and every edit's result prints `Imports.Fixed.said()`.
So one step after the import fix, `FromFields.complete(before, fixed, rel)`: for each class whose
final instance fields have no value, one of them new in this edit, with no initializer and no
constructor but stubs, write `FromFields.constructor` for all of them and place it with
`Declarations.plan` after the last field, which also replaces a stub of the same parameter types
(the test-first case option 2 meets). The note goes in the fixed notes; the record hint is a
syntax check of what `convert_to_record` takes.

Goldens first: `add_declaration_final_fields_constructor`, `_replace_stub`, `_record_hint`, and
`add_declaration_final_fields_refused` changed to a class with a constructor, still refused.

## Notes

- hand: the goldens, resources, by a shell heredoc and a Python script for the expected spacing.
- bulk: `add_declaration` of `complete`, `blankFinals`, `path`, `fitsRecord` to `FromFields`, 125
  lines, all new behavior.
- plan: `Declarations.isStub` made package-private, for `blankFinals`.
- plan: the expected files changed to `add_declaration`'s spacing (a blank line between added
  fields) and, with a stub, the constructor in the stub's place, before the fields added at the end.

## Measured

`bin/session-report`: 21 tool calls; through jrefactor 10 edit steps (docs included), 0 refusals;
`bin/jrefactor` 1 (an inspect); by hand 2 (the goldens and their expected spacing); large writes 2
(the goldens' heredoc, fixture Java in resources; `FromFields.complete` and its helpers); denied 1
(a grep of `Gate.java`, refused by the read guard; redone through `inspect`); reads: inspect 3.

## Assessment

- **Through the tools:** the Java change in one batch (an `add_declaration`, two `replace_text`);
  the goldens are resources, by hand.
- **Large writes:** one, new behavior.
- **Did the plan hold?** Yes.
- **Reading:** `Gate.refusal`, `FromFields.constructor`, `Edits.gated`, `Edits.write`,
  `Imports.Fixed`, to find the one place every edit passes and how a result is worded.
- **One change that would have helped:** none needed.

## Follow-ups

- Option 2: a stub constructor from a test's call assigns the fields its arguments match by type.
- An A/B run of the reservation and value-classes tasks would show the turn saved.
