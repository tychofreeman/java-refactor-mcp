# invert_if (the seventh of the ten higher-level tools)

**Item:** an if with an else: swap the branches and negate the condition.
**Source:** the composite comparison of 2026-11-04 (gorefactor's invert_if), option B.
**Date:** 2026-11-04

## Choice (the user, 2026-11-04)

The negated condition is written as: comparisons simplified (`==` and `!=` swap, `<` and `>=`,
`<=` and `>`), with a `!x` condition losing its `!`, and anything else wrapped as `!(...)`
(or `!x` for a name or a call). Chosen over: always wrapping in `!(...)` (correct, reads worse)
and refusing every condition that is not simple.

Floats: an ordering comparison (`<`, `<=`, `>`, `>=`) on a float or double is refused, since
`!(a < b)` is not `a >= b` when a value is NaN. `==` and `!=` are safe on floats.

## Plan

1. Find the if statement that starts on the line. Refuse none, several, an if without an else,
   and an else-if chain (swapping would make a dangling else).
2. Negate the condition as above.
3. Replace the whole if with `if (negated) ELSE else THEN`, the branches' own text kept.
4. Apply through `Edits.gated`.

Arguments: `file`, `line` (the line the if starts on).

## Golden cases (written first)

- `invert_if_simple_comparison`: `a == 0` becomes `a != 0`, the branches swap.
- `invert_if_negation`: `!done` becomes `done`.
- `invert_if_method_call`: `a.isEmpty()` becomes `!a.isEmpty()`.
- `invert_if_wraps_and`: `a > 0 && b > 0` becomes `!(a > 0 && b > 0)`.
- `invert_if_refused_float`: `a < 1.0` on a double is refused (NaN).
- `invert_if_refused_no_else`: an if with no else is refused.
- `invert_if_refused_else_if`: an else-if chain is refused.

## Notes

- `hand:` the seven golden cases' `in/` and `out/` files, written before the tool existed.
- `hand:` `InvertIf.java` (about 170 lines) and its registration, written by hand, as the earlier
  new tools were. New behavior: the negation rules and the float check.

## Measured

Not measured (no session log for this branch). Counts from the notes: 0 refusals hit while writing;
the first `verify` run is in the PR's CI.

## Assessment

- **Through the tools or around them:** the tool itself could not write its own body; the class and
  registration were written by hand.
- **Did the plan hold?** Operations 1-4 were used as planned. The `!x` rule (drop the `!`) was added
  to the plan; it is what the choice's "simplify" implies for a boolean name.
- **One change that would have helped:** nothing blocked.

## Follow-ups

- Done in this PR: the tool, goldens, docs, full verify.

- Invert an else-if chain by inverting its inner if.
