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

- `read:` `Stubs.resultType`, to see how a stub's result type is chosen from
  its parent node. The parent cases are the same shape each time, so the new
  case follows the existing ones.
- Red golden `stub_enhanced_for`: refused, "Can only iterate over an array or
  an instance of java.lang.Iterable", as the friction entry says. The
  hypothesis held: the call's parent was not handled, so its type was
  `Object`.
- `hand:` the new case and its import were written by Edit; `Stubs` is
  java-refactor-mcp's own source, which no tool edits here.

## Measured

Golden `stub_enhanced_for`: red before (refused), green after (the stub is
`List<String> items()`, with `import java.util.List;`). `./mvnw verify`
passes; spotless clean. The session-log hook was not measured for this
session.

## Assessment

- **Through the tools or around them:** the change is in `Stubs`, which the
  tools do not edit; the golden went through `add_declaration`, the case
  that failed.
- **Did the plan hold?** Yes. The hypothesis was confirmed by the red golden,
  and the one change was the parent case the plan named.
- **Reading:** `Stubs.resultType`, its parent cases.

## Follow-ups

- A loop variable of a primitive type (`for (int n : items())`) gives
  `List<int>`, which does not compile, so the stub is refused. Not
  reproduced; needs its wrapper type, which is not done.
