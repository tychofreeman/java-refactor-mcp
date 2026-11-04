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

- `read:` `Stubs.method` and `removed`, to find what the removal check
  compares. `removed` looks only in the type the call's owner names.
- `plan:` the hypothesis (a label mismatch for a nested type) was wrong. Two
  nested-type probes (a qualified caller and an unqualified caller inside the
  nested class, `stub_after_delete_nested` and `stub_after_delete_outer`'s
  predecessor) and a batch with `check: "end"` were all refused correctly, so
  they were removed. The bug needs a deleted method of an outer type called
  unqualified from an inner one, as `Stubs.label` was from `Stubs.Round`.
- `read:` commit 542b9ae, which removed `Stubs.label`'s callers' label
  helper, to find the exact shape of the original case.
- Cause: an unqualified call in a nested class has the nested class as its
  owner, so `removed` checked only `Round`. The deleted method was in the
  outer class, so `Round` had no such method, and a stub was declared on it.
- `hand:` the fix (`Stubs.removedAround`) and the golden were written by Edit.
  No tool covers editing `Stubs` itself; the change is a few lines.


## Measured

Golden `stub_after_delete_outer`: red before the fix (the deletion went
through and `Inner.label(int)` was declared back), green after (refused,
"deleting Outer.label(int) would add compile errors"). `./mvnw verify`: 170
tests, 2 skipped, spotless clean. The session-log hook was not measured for
this session.

## Assessment

- **Through the tools or around them:** the deletion itself went through
  `replace_declaration`, which is the case that failed; the fix is in `Stubs`,
  which the tools do not change.
- **Did the plan hold?** The plan's first hypothesis was wrong; the
  reproduction (step 1) found the cause, so the one small edit was
  different from the plan's (a check across enclosing types, not a label
  change). The plan's order held: red golden, then the edit.
- **Reading:** `Stubs.method`, `removed`, one commit's diff.

## Follow-ups

- The friction entry of 2026-11-03 (`docs/friction-log.md`, "deleting a helper
  still called in the same file declares a stub") is resolved by this change.
  Its entry is kept as evidence; no new entry is needed.
- A deleted member called from a sibling nested class (not an enclosing one)
  is not covered by `removedAround`: the call's enclosing types do not include
  the sibling's outer class. Not reproduced; not done.
