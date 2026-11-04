# A stub used in an enhanced for

- **Item:** the friction entry of 2026-11-03 ("a stub called in an enhanced
  for is not iterable"), chosen by the user from `what's next?` (2026-11-04).
- **Date:** 2026-11-04
- **Branch:** stub-enhanced-for

## Plan

The change, at the highest level: a stub whose call is the expression of an
enhanced `for` is typed as `java.util.List` of the loop variable's type, so
the loop compiles and the stub is not refused.

Operations, in order:

1. Red golden: `add_declaration` of a method containing
   `for (String s : items()) {...}` with `items()` undefined. Expected: a stub
   `List<String> items()` in the class, and the method added.
2. `Stubs.resultType(Expression use)` already types a stub from its parent
   (assignment, return, condition, argument). Add the case for a parent that
   is an `EnhancedForStatement` whose expression is the call: the result is
   `List<` the type of the loop's parameter `>`, and `java.util.List` is
   added to the imports the stub uses.
3. The one small edit that changes behaviour: that parent case in
   `resultType`.

Hypothesis, to confirm in step 1: `resultType` falls through to `Object`
for this parent, so the loop's expression is `Object`, which is not
iterable.

## Notes

