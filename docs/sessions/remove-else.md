# remove_else (the eighth of the ten higher-level tools)

**Item:** an if whose then-branch cannot finish normally: its else is dropped and the else's
statements follow the if.
**Source:** the composite comparison of 2026-11-04 (gorefactor's remove_else), option B.
**Date:** 2026-11-04

## Rule (no choice put to the user)

Removing an else is only the same code when the then-branch never reaches the end of the if (it
ends in return, throw, break, continue or yield). Otherwise the else's statements would run after
the then-branch as well. Refused otherwise, with the reason. An else-if is allowed: its inner if
follows the outer one.

The if must be a statement of a block: directly under another if or loop without braces, the
else's statements would land outside that branch.

## Plan

1. Find the if that starts on the line; refuse none, several, no else, the if not in a block, or a
   then-branch that can finish normally.
2. Write the if with its then-branch, then the else's statements, dedented to the if's indentation.
3. Apply through `Edits.gated`.

Arguments: `file`, `line` (the line the if starts on).

## Golden cases (written first)

- `remove_else_simple`: the else's two statements follow the if, dedented.
- `remove_else_refused_can_finish`: a then-branch that assigns and falls through is refused.
- `remove_else_refused_not_in_block`: an if that is the body of another if without braces is refused.

## Notes

- `hand:` the three golden cases' `in/` and `out/` files, written before the tool existed.
- `hand:` `RemoveElse.java` (about 200 lines) and its registration, written by hand, as the earlier
  new tools were. New behavior: the dedent of the else's lines and the then-branch check.

## Measured

Not measured (no session log for this branch). 0 refusals hit while writing.

## Assessment

- **Through the tools or around them:** the tool could not write its own body; written by hand.
- **Did the plan hold?** Steps 1-3 were used. The dedent was not in the plan; it is needed so the
  else's statements sit at the if's indentation.
- **One change that would have helped:** nothing blocked.

## Follow-ups

- Done in this PR: the tool, goldens, docs, full verify.

- Remove an else whose then-branch ends in an if that always returns (the refusal is conservative).
