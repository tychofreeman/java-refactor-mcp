# A stub after a deleted method that a subclass inherits

- **Item:** the follow-up of `stub-after-delete.md` (#66): a method deleted
  from one nested class and still called from a sibling was not covered by the
  enclosing-type fix. Chosen by the user from `what's next?`, 2026-11-04 (all
  remaining candidates, in order: this, then #22, then #39).
- **Date:** 2026-11-04
- **Branch:** stub-after-delete-inherited

## Plan

The change, at the highest level: a stub must not be declared for a method
that an edit removed from a supertype of the type the call is in.

Operations, in order:

1. Reproduce. The follow-up's sibling case cannot happen as written: an
   unqualified call cannot reach a sibling's instance method. The real shape
   is inheritance: `Child extends Base`, `Base.g()` deleted, `Child.f()` calls
   `g()` unqualified and inherits it. Red golden: the deletion is refused.
2. The removal check (`Stubs.removedAround`) looks at the call's type and its
   enclosing types only. Extend it to the supertypes of those types, looking
   each up in its own file (`sourceOf`), the way the enclosing types are.
3. The one small edit that changes behaviour: that extension in
   `removedAround`.

Hypothesis, to confirm in step 1: `Child` gets a stub `g()`, which compiles
and quietly takes the call from `Base.g`, so the deletion is not refused.

## Notes

