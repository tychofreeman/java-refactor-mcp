# A constructor stub sets the fields its arguments fit

- **Item:** the user's request (2026-11-07), option 2 of three offered after #271, after option 1
  (#272): a constructor declared from a use assigns fields instead of throwing.
- **Date:** 2026-11-07
- **Branch:** stub-constructor-assigns

## Plan

In `Stubs.Round.constructor`, before the throwing stub is wanted: `setting(target, owner,
arguments, head)` matches each argument to the one field of `owner` it fits (exact type by
binding, from `owner.getDeclaredFields()` in the same parse as the arguments; assignable, which
for a final field needs the target's source, as bindings do not say whether it has a value; a
name breaking a tie), and returns the assigning constructor, or null to keep the stub. The label
says what it sets. Goldens first: `stubs_constructor_sets_fields` (a tie broken by name, a
final field with a value skipped) and `stubs_constructor_fields_ambiguous` (two `String` fields
and a literal: the old stub).

How it meets #272: a class written test-first has no fields when its constructor is declared, so
this does not apply and the stub throws; when the fields are added, #272 replaces that stub with
the assigning constructor.

## Notes

- hand: the goldens, resources, by a shell heredoc.
- bulk: `add_declaration` of `setting`, 64 lines, new behavior.

## Measured

`bin/session-report`: 9 tool calls; through jrefactor 9 edit steps (docs included), 0 refusals; by
hand 1 (the goldens); large writes 2 (the goldens' heredoc, fixture Java in resources; `setting`);
denied or failed 1; reads: inspect 1 (the log began after the first reads of `Stubs`, made while
#272 was in CI).

## Assessment

- **Through the tools:** the Java change in one batch (`replace_text` of the call site, two
  `add_declaration`s); the goldens by hand, as resources.
- **Large writes:** one, new behavior.
- **Did the plan hold?** Yes.
- **Reading:** `Stubs.Round.constructor` and `Stub`, to place it and see how a label is shown.
- **One change that would have helped:** none needed.

## Follow-ups

- The match is by exact type: an `Integer` argument does not fit an `int` field, nor a subclass a
  field of its supertype.
- An A/B run of the value-classes task would show whether the two changes save the turns measured
  in `repeated-constructions.md` and `ab-hints.md`.
