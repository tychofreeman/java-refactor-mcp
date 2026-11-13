# Enum pins and a refusal that names what to send

- **Item:** roadmap #143 (order 136): increment 3 of `docs/sessions/enums-sketch.md`
- **Date:** 2026-11-13
- **Branch:** enum-pins

## Plan

Two goldens for what works today and nothing pins (`rename` of a constant with `case` labels in
another file; `add_parameter` on an enum's constructor, which #142 already added as
`add_parameter_enum_constants`), and one refusal reworded: `add_declaration` `constructor` on an
enum says what to send instead. No other code.

## Notes

- plan: `rename_enum_constant_case_label` passed as written once its arguments were right
  (`rename` takes `line`; my first `args.properties` gave only `name`): a pin, as the sketch said.
- plan: `add_declaration_constructor_refused_enum` red first, with the old wording ("Color is not
  a class; constructor writes a class's constructor (a record has its canonical one)").
- bulk: one `replace_text` in `FromFields.constructor`, the enum case before the class check.
- hand: nothing in Java by hand.

## Measured

Not measured: the session-log hook is not loaded in this session. From the notes: 1 tool call,
0 refused, 0 hand edits of Java.

## Assessment

Through the tools; the plan held. The refusal's advice names `add_parameter`, which gives every
constant one value; when a field on an enum is built (increment 4) the advice should name that
form, and that PR changes this wording.

## Follow-ups

- None beyond the sketch's increments.
