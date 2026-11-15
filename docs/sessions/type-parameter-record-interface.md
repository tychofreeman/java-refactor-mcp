# add_parameter with type_parameter: records and interfaces

- **Item:** issue #375 part 1, roadmap item 512
- **Date:** 2026-11-15
- **Branch:** type-parameter-record-interface

## Plan

`TypeParameters.toClass` refuses a record or an interface up front, and its helpers take a
`TypeDeclaration`. Make the change easy first: a `typeParameters(AbstractTypeDeclaration)` helper,
`member` walking `bodyDeclarations`, the helpers' `target` retyped to `AbstractTypeDeclaration`
(`change_type`). Red: goldens for a record with `members` (component plus accessor, compact
constructor), a record's explicit canonical constructor, an interface with a constant, a default
method and an implementing class. Green: drop the precondition; a record component in `members`
retypes the component, its written accessor and the canonical constructor's parameter (Java
requires them alike, so this is no choice); an interface's fields count as static. Then a method
of a record, which `toMethod` refused.

## Notes

- read: `inspect` of `TypeParameters` (outline, `add`, `toClass`, `typed`, `member`, `toMethod`),
  `CallTargets.places`/`place`; `javap` for `RecordDeclaration` and `IMethodBinding`.
- read: one `sed -n` of six lines of `TypeParameters.java` to check a precondition's text; the
  guard is not loaded in this session, and it should have been `inspect`.
- refusal: `extract_function` then `replace_statements` in one batch: the call I wrote passed the
  arguments in another order than the extracted method took them, and the stub collided with it
  ("same erasure"). Retried with the extraction alone, inspected, then the call.
- found: `CallTargets` knew a record's implicit accessor by its binding key, which changes once
  the record is generic, so `new Pair(...).key()` was refused as running another method. It now
  places an implicit accessor and canonical constructor by the record, as a default constructor.
- Java edits: 8 tool calls (batches of 11, 5, 3, 2+retry, 2 steps; replace_statements and
  replace_text in CallTargets); none by hand.

## Measured

Not measured (no session-log hook in this session).

## Assessment

Through the tools. The refactoring to `AbstractTypeDeclaration` went in one batch of 11 steps
from one read. No probe: the change adds type arguments, and `CallTargets` with the types check
already holds what runs.

## Follow-ups

Part 2 of #375 (an explicit type argument at a call) in its own PR.
