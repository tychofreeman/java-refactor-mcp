# fold: name an overload, refuse an ambiguous one

- **Item:** issue #486 (code review of 2026-11-13, extract-inline #10), group 2 of tracking issue #532
- **Date:** 2026-11-13
- **Branch:** fix-fold-overload

## Plan

Reproduce with two overloads, `twice(int)` and `twice(long)`: `fold Util.twice` should be refused
as ambiguous, and `Util.twice(long)` should fold the long one. Then `Fold.method` resolves through
`Declarations.find`, which handles both, and `run` takes the type and method names from the part
before the parentheses.

## Notes

- plan: `Util.twice` folded nothing of `twice(long)`'s matches and was not refused, having taken
  `twice(int)`; `Util.twice(long)` was refused "no method".
- read: `inspect` with `names` (`Fold.method`, `Fold.run`, `Fold.overrider`, `Fold.anchor`).
- refusal: a batch of `replace_declaration` and three `replace_text` was refused at the end check:
  step 4 dropped an argument of `overrider` before its parameter; `retry` removed that step, then
  `remove_parameter` dropped the unused parameter, carrying the call.
- `fold_left_overload` relied on the first overload being taken; its `name` is now
  `Main.show(Object)`.
- hand: the golden files and the docs.

## Measured

Not measured: the session-log hook is not loaded here. All Java through the tools.

## Assessment

Through the tools; the refused step taught the right order (the call before the parameter, or
`remove_parameter`, which does both).

## Follow-ups

- `Fold.overrider` still parses a `Holding` of the method's name, a third resolution of the
  method (the issue's cost finding); running it over the main `Holding` needs that holding to
  hold every file naming the method, which the anchor does not promise.
