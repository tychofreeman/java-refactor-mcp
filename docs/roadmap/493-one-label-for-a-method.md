---
order: 483
item: 493
status: Done
---

## Item

One label for a method in every result and refusal: the class with its enclosing types, the parameter types as source writes them, erased only when two methods of the class would read alike (`Names.methodLabel`, `Names.memberLabel`, `Declarations.methodLabel`); every label taken back as `name`

## Status

Done (2026-11-14): `Names.sourceLabel` and `Names.signature` folded into `Names.methodLabel`; `Names.takes` matches a label's parameters for `implement` and `test_for`; `Declarations.overloads` compares with qualifiers dropped last; `NamesTest.everyMethodLabelNamesItsMethodBackInEveryTool`

## Evidence

The owner's choice of 2026-11-14, option (a) of the follow-ups of #501 (`docs/sessions/one-label.md`): `Names.methodLabel` printed erased types (`Shop.Cart.add(List, int)`), `Names.sourceLabel` source types with the owner alone (`Cart.add(List<String>, int)`), so one tool's label was not always another's
