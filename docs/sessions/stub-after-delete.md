# A stub declared back after a delete

- **Item:** the open friction entry of 2026-11-03 ("a stub removed by a
  deletion is declared back"), chosen by the user from `what's next?`.
  The session notes say a member the edit removed is not declared again,
  so this entry contradicts the notes.
- **Date:** 2026-11-04
- **Branch:** stub-after-delete

## Plan

The change, at the highest level: a stub must not be declared for a member
the same edit removed, whatever the type it belongs to is called. The
behaviour is in the removal check that `Stubs` runs before it declares a
stub.

Operations, in order:

1. Red golden: a deletion (`replace_declaration` with empty content) of a
   method whose callers stay, with the caller's type nested in another type,
   so the stub's label and the removal's label may differ. Expected: the
   deletion is refused, as a deleted method used elsewhere is, and nothing
   is declared.
2. Find the label each side builds for the type (`Stubs` and the removal
   check, `removed(target, type, simple)`), with a golden to see which one
   differs.
3. The one small edit that changes behaviour: build both labels the same way
   (`Names.typeLabel`), so the removal check matches the stub's type.

Hypothesis, to confirm or drop in step 2: the removal check compares a label
that does not match the stub's label for a nested type.

## Notes

