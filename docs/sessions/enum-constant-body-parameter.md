# `add_parameter` reaches an enum constant with a body

- **Item:** roadmap #142 (order 135): increment 2 of `docs/sessions/enums-sketch.md`, two bugs
  with reproductions (section 7)
- **Date:** 2026-11-13
- **Branch:** enum-constant-body-parameter

## Plan

`MethodFamily.findCalls` matches a constant by `resolveConstructorBinding()`; for a constant with
a body that is the anonymous class's constructor, so the constant is not a call of the family and
the gate refuses. Map that binding to the superclass constructor before the comparison, in one
helper. The refusal for an enum with no constructor: make its advice true (name what works for
an enum) rather than widen `add_declaration` `constructor`. Goldens first.

## Notes

- read: `MethodFamily.of`, `findCalls`, `constructorOf`, `FromFields.constructor`,
  `Signatures.key` and `Call` by `inspect`.
- plan: a run on a scratch project showed `new Base(1) { ... }` fails the same way as the enum
  constant ("The constructor Base(int) is undefined"): one cause, so one fix and a golden for
  each (`add_parameter_anonymous_class`).
- plan: goldens red first: `add_parameter_enum_constant_body`, `remove_parameter_enum_constants`,
  `add_parameter_anonymous_class`, `add_parameter_refused_enum_no_constructor`.
  `add_parameter_enum_constants` (the sketch's 2.3) and `add_declaration_enum_constructor` (what
  the refusal now advises) passed before the change: pins. The pins name the constructor
  `Color()`, not `Color`, so they stay as they are when `name` an enum comes to mean a field
  (increment 4).
- hand: two goldens of mine were wrong, not the code: a number in `args.properties` is sent as a
  number ("value must be a string; got 1"), so `value` went to `value.txt`.
- bulk: one batch of four steps (`add_declaration` of `Signatures.constructed`, three
  `replace_text` in `MethodFamily`), check `end`; green on the first run.
- hand: the rebase onto `main` conflicted in `Signatures.java` (another PR added a method at the
  same end of the class). A script of mine that keeps both sides of a docs conflict was run on
  it and dropped a `}` and a `/**`; put back with the Edit tool (three lines), and the script now
  takes only `.md` files.
- plan: the choice the item left open. Chosen: reword the refusal for an enum to name
  `add_declaration` with content `Color() {}` (one expression in `constructorOf`). Not chosen:
  `add_declaration` `constructor` for an enum. It takes field names and is refused with none, and
  a constructor with parameters added to an enum does not compile until each constant passes
  them, so the advice would still fail for the enum the refusal is about.

## Measured

Not measured: the session-log hook is not loaded in this session. From the notes: 1 batch
(4 steps), 0 refused calls, 1 hand edit of Java (a merge conflict, three lines).

## Assessment

Through the tools; the plan held. One change that would have helped: `GoldenTest` treats
`value=1` in `args.properties` as a number where the tool wants a string, which every case
with a numeric `value` trips on; noted as a follow-up.

## Follow-ups

- `ByLine`, `SafeDelete`, `ThrowsCallers` and `Signatures.addComponent` read
  `resolveConstructorBinding()` without `Signatures.constructed`: by reading, a constructor used
  only by an anonymous subclass or a constant with a body is not found by `line`, and may be
  deleted as unused. Not reproduced.
- The advice for a class with no fields ("add_declaration (constructor)") is refused too, since
  `constructor` needs at least one field (by reading).
- `GoldenTest.value` could keep a `value` argument a string.
