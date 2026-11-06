# type_switch_to_method

- **Item:** roadmap #127 (gorefactor's type switch pair, parity section 3). The user's choice
  (2026-11-06): one tool, Go's prepare step inside it, over two tools and over a composition.
- **Date:** 2026-11-06
- **Branch:** type-switch-to-method

## Plan

`type_switch_to_method` (`file`, `line` of a switch expression, `name`): a switch over a value
whose static type `S` is a project interface or abstract class, every case a type pattern
(`case Circle c -> expr`), becomes `s.name()`: `S` declares `name()` (abstract), and each case's
type gets `@Override public R name()` returning the case's expression, the pattern variable
becoming the receiver (`c.r()` becomes `r()`, a bare `c` becomes `this`). `R` is the switch's
type. First version, refused saying why: a switch statement, a `default`, a guard, a block case,
a case type that is not the project's or already declares `name()`, a branch that uses a local,
a parameter, `this` or an instance member of the method's class (it would mean another thing on
the case's type).

Derivation: golden first; `TypeSwitch` from `declaration`, members by `add_declaration`;
`MemberMove.appendTo` widened to any type declaration (a record); the `Def`.

## Notes

- refusal: the first batch, for `Name` and `QualifiedName` (JDT's, among others); resent with
  `imports` (244 lines twice). I also found my own bug while resending: `fileOf` threw
  `IOException` inside a precondition lambda.
- refusal: a fix batch, for `v.getType()` left in refusal texts after the variable became a name;
  the refusal named it, and one more step fixed it.
- The pattern variable's binding was null: `getPatternVariable()` is a node with no bindings;
  `getPatternVariable2()` is the resolved one (`javap` showed both). Session-notes trap.
- `MemberMove.appendTo` widened to `AbstractTypeDeclaration` (records get members too).

## Measured

Through jrefactor: 2 batches refused then resent, 3 fixes, the docs by script; by hand the
goldens (resources) and this record.

## Assessment

Through the tools. The import refusal in a new file, again (the 2026-11-06 friction entry).

## Follow-ups

- A switch statement (void), a `default` (a default method on the interface), an `instanceof`
  chain, and values of the method passed as parameters of the new method.
