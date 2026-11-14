# inline: a qualified use of the class's own static member

- **Item:** issue #621, the owner's follow-up of 2026-11-14 (from the #521 work's friction entry)
- **Date:** 2026-11-14
- **Branch:** inline-qualified-static

## Plan

`InlineFunction.usesMembersOf` should ignore a name after a qualifier. One statement at the top of
its visitor (`if (Nodes.qualified(n)) return true;`), its doc comment, after a red golden (a
qualified call and field across packages) and a probe (the caller declares the same names).

## Notes

- read: `inspect` with `names` for `InlineFunction.usesMembersOf` and `Nodes.qualified`, then the
  call site's range.
- refusal: `insert_statements` with `position` ("does not take position ... where"); `batch`
  `retry` with step 1 fixed.
- plan: a caller reaching the method by static import is refused by the type check (its import
  names the deleted method): pinned as a refusal, left as a follow-up.
- hand: none in Java. Goldens, probe and docs are resources and text.

## Measured

Not measured: the session-log hook is not loaded here. Two `bin/jrefactor` calls for the Java
change (one batch, one retry), no Java by hand.

## Assessment

Through the tools. The plan held: two lines of behavior.

## Follow-ups

- `inline` of a static method called through a static import: drop the import and import the
  class (the qualified body needs it), or refuse in words naming the import.
