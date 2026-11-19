---
order: 521
item: 531
status: Done
---

## Item

`replace_expression` with `everywhere` and `$name` holes, as gorefactor's #676: a pattern anchored at `line` (holes stand for any expression; the anchor fixes each hole's type and each other name's meaning), replaced there or, with `everywhere`, at every expression of the project that matches the same way, `new` getting each hole's source.

## Status

Done (2026-11-19, `docs/sessions/replace-expression-everywhere.md`): `ExpressionPattern`. Left: gorefactor's #690 (a batch step's expression found again by its text on a line an earlier step rewrote), and a match inside the method `new` calls, which is replaced too.

## Evidence

gorefactor's cost-history 30 and 33: on freight-xxl's routes, per-use edits took 628 steps in 8 runs (6/8 passed); with `everywhere`, 153 steps and 8/8. The parity comparison of 2026-11-19 (`docs/sessions/ab-design-routine.md`, follow-ups) found it missing here.
