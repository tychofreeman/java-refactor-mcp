# One label format for methods and types

- **Item:** issue #501 (code review: core #8, moves #16, extract-inline #9)
- **Date:** 2026-11-14
- **Branch:** one-label

## Plan

Put the label helpers in `Names`, where `typeLabel(ITypeBinding)` already is: move
`Signatures.label` there as `methodLabel`, `Skeleton.signature` as `signature`, `Implement.label`
as `sourceLabel`, `Inspect.chain` as `typeLabelAt` (generalized to any node); then route
`TestFor`, `Skeleton`, `Generated.key`, `ThrowsCallers.stopOf` and `MoveToPackage.label` through
them. Change no output a golden pins; where output changes, pin it with a golden that fails first.
Leave the two formats that disagree (erased and written parameter types) for the owner.

## Notes

- read: `inspect` `names` of each formatter, `pattern` for their callers; `#483` had already made
  `Declarations.methodLabel` the handed-back name and shared `Declarations.typePath`.
- plan: `implement` parses by stripping spaces, so `Base.describe(int, String)` still names one
  method when sent back; erased labels would not (it compares source-typed names).
- red: `move_type_to_package_widened_method_parameters` (`Shop.price()` for `price(int, String)`),
  `add_declaration_implement_refused_overloads` and `_unknown_lists` (no spaces): each failed on
  `main`.
- refusal: `rename` to an overloaded name (friction log); `rename` needs a line in a batch.
- bulk: four batches, six single calls.

## Measured

Not measured: the session-log hook is not loaded here (the CLI log, `JREFACTOR_LOG`, is).
From the notes: no Java by hand.

## Assessment

Through the tools. Two behavior changes, both in what a result or refusal prints, both pinned.

## Follow-ups (the owner's choice)

- Erased against written types: `Names.methodLabel` (erased, enclosing types: `Outer.Cart.add(List,
  int)`, about 25 refusals and results) against `Names.sourceLabel` (source types, owner alone:
  `Cart.add(List<String>, int)`, `implement` and stubs) and `Declarations.methodLabel` (written,
  erased only when two overloads are written alike). Options: (a) every label as
  `Declarations.methodLabel` does, written types unless ambiguous: closest to what the model reads
  in source, needs a declaration (or the binding's written form) at each of about 25 sites, and
  changes many pinned refusal texts; (b) every label erased, as `Names.methodLabel`: one rule, but
  `implement`'s parser must then take erased names, and `List<String>` reads as `List`; (c) keep
  both, documented (now): no change, and a label from one tool may still not be the one another
  prints, though `Declarations.find` takes written and erased parameter types alike.
- One parser for `Type.member(params)`: `Qualified.of` splits at the first dot, `Fold` takes the
  last two segments, `Implement` the last dot, `TestFor` guesses by case. Options: extend
  `Qualified` with `(params)` and nested types and port the four (input `Outer.Inner.m` then
  works everywhere; each port needs a golden); or leave until a refusal shows a model sending one
  tool's label to another.
- `MoveType.label` (names an owner as it will be after the move) and `Rename.describe` (prose,
  "the method Cart.add") keep their own forms on purpose.