# A primitive loop variable over a stub

- **Item:** the follow-up of `stub-enhanced-for.md` (#68): a primitive loop
  variable (`for (int n : items())`) gave `List<int>`, which does not
  compile, so the edit was refused. Chosen by the user from `what's next?`
  and this request, 2026-11-04.
- **Date:** 2026-11-04
- **Branch:** stub-enhanced-for-primitive

## Plan

The change, at the highest level: the element type of a stub's `List` is
the loop variable's type, boxed when that type is primitive, because a
`List` holds objects.

Operations, in order:

1. Red golden: `add_declaration` of a method with `for (int n : items())`,
   `items()` undefined. Expected: the stub is `List<Integer> items()`.
2. In the enhanced-for branch of `Stubs.resultType`, box a primitive loop
   variable's type before it is written. Boxing is a small map from the
   primitive's name to its wrapper, in `Stubs`.
3. The one small edit that changes behaviour: that boxing step.

Hypothesis, to confirm in step 1: the branch writes `List<int>` as the
element type, and the gate refuses it as not a type argument.

## Notes

