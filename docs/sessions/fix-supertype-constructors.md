# extract_supertype kind=class under an abstract or argument-taking superclass

- **Item:** issue #409, found by the Commons Lang stress run (`docs/sessions/stress-new-options.md`);
  roadmap #179 (order 172), the owner's request of 2026-11-13; also the follow-up of
  `docs/sessions/extract-supertype-generic-class.md`
- **Date:** 2026-11-13
- **Branch:** fix-supertype-constructors

## Plan

The new class sits between the class and its old superclass, so it must be what the old
superclass needs: abstract when an abstract method is left unimplemented by it (or a member it
takes is abstract), and with constructors that pass the class's `super(...)` arguments on.
Derivation: make `Skeleton.unimplemented`'s matching reusable for "a class extending T and
declaring these methods" (a refactoring, while green); then in `ExtractSupertype`, decide
`abstract` from the old superclass's binding and the moved members, and write one pass-through
constructor per old constructor the class's `super(...)` calls resolve to, from the call's
binding (so a generic superclass's parameter types are in the class's type variables, which the
new class declares). The class's calls stay as written.

## Notes

- read: the gate refused all three reproductions at "creating Sized" / "pulling Shape.area up";
  red confirmed with `src/main` stashed (three goldens and the probe).
- plan: constructors copied are only those the class's `super(...)` calls use, plus a no-argument
  one when another constructor calls `super()` implicitly; each keeps its old access and
  `throws`. Overload resolution in the new class runs over a subset holding the old choice, so
  each call picks the same constructor (the probe has two overloads, `Object` and `String`).
- refactor: `Skeleton.leftAbstract(type, more)` extracted from `unimplemented` before the change.
- read: the goldens then differed only by a blank line left before the closing brace of the
  class the last member left. `MemberMove.removal` looked one line too far up for the blank line
  before a last member, and its "skip the newline" step had already eaten an empty blank line
  after a member, which hid it. Rewritten: the line after, else the line before. Every golden
  passed unchanged; pinned by these goldens.
- bulk: `ExtractSupertype` through one `batch` (three `replace_text`, `replace_declaration` of
  `create`, `add_declaration` of three helpers); a second `replace_text` fixed my own logic for
  the implicit constructor.
- hand: the contract, the design row and the old record's follow-up by a Python string replace
  (docs); a throwaway program in `/tmp` to print `MemberMove.removal`'s ranges while finding the
  blank-line cause. Goldens and the probe written as plain files.

## Measured

Not measured: the session-log hook is not loaded here. From the notes: 4 tool calls for code
(two batches), no refusals.

## Assessment

Through the tools for every Java change. The plan held; the blank-line defect was found because
the goldens were written by hand first, not taken from the tool's output.

## Follow-ups

- The defaults in roadmap #179 (which constructors, their access, `abstract` in the result) are
  to reopen if the owner prefers otherwise.
- A generic constructor of the old superclass (`<U> Base(U u)`) is passed on with the types the
  call inferred, not as a generic constructor; not seen in the stress run.
