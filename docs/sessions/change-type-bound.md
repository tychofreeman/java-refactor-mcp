# change_type with type_parameter: bounds

- **Item:** roadmap #159 (order 152): increment 7 of `docs/sessions/generics-sketch.md`
  (examples 15 and 16). The owner decided the sketch's open questions by its recommendations
  (2026-11-13): slice 3 on `change_type` (a), and a cast that now checks the bound reported
  through `Exceptional.change` (question 6, a).
- **Date:** 2026-11-13
- **Branch:** change-type-bound

## Plan

Refactor first: the call-types snapshot of `toMethod` becomes `callTypes` (extract_function),
and `CallTargets`'s place map becomes `places`. Then `CallTargets` records which methods override
which and refuses a change that loses or gains one (example 16). Then `TypeParameters.bound`,
routed from `change_type` when `type_parameter` is given: find the type parameter on the class or
on each declaration of the method's family, replace the text after its name, gate, compare a
method's call types, `CallTargets`, and report casts whose erasure changed. Goldens from the
sketch's increment 7 list; probes for the raw override (refused) and the cast (changed).

## Notes

- bulk: `bound` (about 230 lines) as one `add_declaration`; `CallTargets`'s override check as a
  `replace_declaration` of `require` and two `add_declaration`s, in one batch.
- plan: the refused override names what the raw subclass overrode, `Box.put(Object)`, not
  `Box.put(T)`: the method of a raw supertype is its erasure. The golden takes that wording.
- plan: the cast probe changed its output as expected (`cast failed` after), so the report is
  real, not cautious.
- hand: goldens, probes and docs (resource and Markdown files).
- read: `inspect` for every Java read.

## Measured

Not measured: the session-log hook is not loaded in this container.

## Assessment

Through the tools for every Java read and write; two `extract_function` calls did the
refactoring steps. No Commons Lang checkout in this container, so `RefactoringStressTest` was
not run before merging, as the sketch asked; the PR says so.

## Follow-ups

- A `T...` parameter's array is created as the new erasure once the bound changes; not
  reported.
- `RefactoringStressTest` could pick `change_type` with `type_parameter`.
