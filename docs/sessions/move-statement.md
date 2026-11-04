# move_statement (the sixth of the ten higher-level tools)

**Item:** swap a statement with the one before or after it in the same block.
**Source:** the composite comparison of 2026-11-04 (gorefactor's move_statement_up and
move_statement_down), option B, after add_supertype (PR #109).
**Date:** 2026-11-04

## Choice (the user, 2026-11-04)

The two statements may swap when they are independent. The user chose "Strict, but allow
names only with a flag" over three options:

- **Strict** (the default): the statements share no name, and at most one has a side effect (a
  call, an assignment, `++`/`--`, a `new`). Never reorders observable effects; refuses some safe swaps.
- **Names only** (`names_only=true`): only a shared name that one writes refuses. Calls may swap
  order. Chosen as a flag, so the default stays the safe rule.
- Rejected: Names only as the default (it reorders two logged calls, the case gorefactor refuses),
  and Strict with a `force` flag (more surface for a model to misuse).

## Plan

Change at the highest level: a statement and its neighbour in the same block trade places. The
text between the two (whitespace, comments) stays where it was.

Operations, in order:
1. Find the statement that starts on the line, as a direct child of a block. Refuse when none,
   when several start on the line, or when it has no neighbour in the direction.
2. Refuse a return, break, continue, throw or yield: moving it changes where the code goes.
3. Check independence for the pair: a shared name (a variable or field one declares or writes and
   the other uses) refuses both rules; a side effect in both refuses the strict rule only.
4. Swap the two statements' text, keep the gap between them, apply through `Edits.gated`.

Arguments: `file`, `line`, `direction` (`up` or `down`), `names_only` (optional, a flag).

## Golden cases (written first)

- `move_statement_up_simple`: `int b = 2;` moves above `int a = 1;`.
- `move_statement_down_simple`: `int a = 1;` moves below `int b = 2;`, the same result.
- `move_statement_refused_shared_name`: `int b = a + 1;` cannot move above the `a` it uses.
- `move_statement_refused_side_effects`: two calls to `print`; strict refuses.
- `move_statement_names_only_allowed`: the same two calls with `names_only=true` swap.
- `move_statement_refused_return`: a `return` cannot move.

## Notes

- `hand:` the golden cases' `in/` and `out/` files, written with `Write` before the tool existed.
- `hand:` `MoveStatement.java` (about 250 lines) and the `Def`, `ALL` entry and handler in `Tools`,
  written by hand, as add_supertype's were. New behavior, not existing code typed again.
- `hand:` the first `verify` failed on Spotless; `./mvnw spotless:apply` fixed the formatting and
  the rerun passed. Not a tool gap: the formatter is the project's check.
- `plan:` a `names_only` shared-name check also refuses a write to a field read by the other
  statement; the plan did not say so. It follows from the rule ("a name one writes, the other uses").

## Measured

Not measured: `bin/session-report` has no log for this branch (the hook was not loaded when this
session started). Counts are from the notes: 0 refusals hit while writing; 1 `verify` run failed on
formatting, then passed (234 tests, 0 failures, 4 skipped).

## Assessment

- **Through the tools or around them:** the tool could not be used on its own construction, so its
  class, registration and goldens were written by hand, as the first ten were.
- **Large writes:** `MoveStatement.java` is new behavior (the independence check and the swap).
- **Did the plan hold?** Operations 1-4 were used. The plan's "names" check was extended to fields
  written by an assignment, which is what the shared-name rule means.
- **One change that would have helped:** nothing blocked; the choice of rule came from the user.

## Follow-ups

- Done in this PR: the tool, goldens, docs, full verify.

- Move a statement out of its block (into an enclosing one), or past a statement in another block.
