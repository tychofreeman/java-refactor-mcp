# type_switch_to_method over an int says the selector is the reason

- **Item:** issue #437, from the Java 17 stress run (`docs/sessions/stress-enum-switch.md`): 306 of
  750 calls were on switches over `int` or `char` with constant labels, refused with the
  type-pattern form's "each case must be a type pattern" (or, with a block case, "an arrow case
  giving an expression"). Roadmap #184.
- **Date:** 2026-11-13
- **Branch:** fix-switch-selector-wording

## Plan

`TypeSwitch.run` reads the cases (`switchCases`, `chain`), which refuse on the first case that is
not a type pattern, and only then checks that the selector's type is an interface or abstract
class of the project. Check the selector first: compute it from the switch (`over`, already read
for the enum test) or the chain's first `instanceof`, refuse with the wording the issue asks for
plus what to do, then read the cases. Goldens first: the issue's `int` switch expression (with a
`yield` block case) and a `String` switch statement.

## Notes

- read: `TypeSwitch.run` from #435's session; `grep` of docs and tests for the old wording (none
  pinned it).
- plan: two refused goldens failed with the case refusals.
- One `replace_text` (an `@@old`/`@@new` section pair) moved the selector and its precondition
  before the cases and changed the wording; the result-type precondition stays after.
- Default (the caller said to say what to do): the refusal ends "Leave the switch as it is, or
  extract_function it to name it as a method here.", over no fix line (the issue's text) and over
  pointing at a constants-to-enum conversion, which is not built (the enum sketch's slice 1).

## Measured

Not measured: the session-log hook is not loaded in this session. One tool edit, no hand edits of
Java.

## Assessment

Through the tools; the plan held. The refusal also names "switch" for an `if` chain, as before.

## Follow-ups

- If constants to an enum (the enum sketch's slice 1) is built, this refusal should name it.
