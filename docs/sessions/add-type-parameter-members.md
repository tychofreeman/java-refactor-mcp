# add_parameter with type_parameter and members

- **Item:** roadmap #157 (order 150): increment 5 of `docs/sessions/generics-sketch.md`. The
  owner decided the sketch's open questions by its recommendations (2026-11-13); this item rests
  on question 2 (a): the named members' signatures only, the gate judging their bodies.
- **Date:** 2026-11-13
- **Branch:** add-type-parameter-members

## Plan

`members` on `add_parameter`: in `TypeParameters.toClass`, before the walk over uses, find each
named member (as `Declarations.find` does), refuse what cannot take `T` (static, an array of
`value` created, a method that overrides a supertype's, nothing to change), and add an edit for
each type node in its declared types that binds to `value`. The edits join the class file's
edits, so the one gate, the diamond check and `CallTargets` judge it all. Goldens from sketch
examples 1, 4, 5 and 6, then the two probes.

## Notes

- plan: the first golden (example 1 with a `same(Box other, List<String> seen)`) was refused by
  `CallTargets`: `other.value.equals(value)` names `Object.equals` once `value` is `T`, where it
  named `String.equals`. The run-time target is the same, so `CallTargets` now passes a virtual
  call whose new target is a method its old one overrides (`Target.overrides`); an overload
  (`print(value)`, the probe) is still refused.
- refusal: a batch deleted `TypeParameters.overridden` before the step that removed its use;
  `retry` does not reach across CLI runs (friction log). Resent whole, reordered.
- plan: the members probe's `copy()` built `new Box(value + "!")`: once `value` is `T` the
  gate refused it ("Cannot infer type arguments for Box<>"), as the sketch's question 3 says it
  should. The probe builds `new Box(value)`.
- hand: goldens, probes and docs (resource and Markdown files).
- read: `inspect` for every Java read; edits through four `bin/jrefactor` batches.

## Measured

Not measured: the session-log hook is not loaded in this container.

## Assessment

Through the tools for every Java read and write. The plan held but for `CallTargets`, whose rule
was too strict for a receiver typed by a type variable; the fix is general (a wildcard receiver
of `change_type` gets the same). No Commons Lang checkout here, so `RefactoringStressTest` was
not run (it does not pick `type_parameter` either).

## Follow-ups

- The gate's refusal for a body that needs the type names the file but no line when JDT gives
  none ("p/Box.java | Cannot infer type arguments for Box<>").
- `RefactoringStressTest` could pick `add_parameter` with `type_parameter` and `members`.
