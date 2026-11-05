# change_type refuses the primitive uses the type decides

- **Item:** the `change_type` finding of `docs/sessions/probes-remaining.md`: a change
  of a primitive type compiled and changed behavior (overflow, division, printing).
  The user chose the rule on 2026-11-05: refuse where a use could mean something else
  (`docs/design.md`, Decisions, with the options it beat).
- **Date:** 2026-11-05
- **Branch:** change-type-primitive-uses

## Decisions (this session)

- **`PrimitiveUses`** is the rule, for a change whose old or new type is primitive
  or boxed: a use that is an operand of arithmetic, a shift, bitwise or string `+`, a
  unary operator other than `!`, `++`/`--`, a compound assignment's target, an
  argument of a call or `new`, a cast, a branch of `?:`, a switch selector, and, for
  a boxed type, `==`/`!=` or a call's receiver, is refused, naming the use and its
  line. Copies, returns and ordering comparisons are allowed; a narrowing copy fails
  to compile and the gate refuses it.
- **All three forms:** a field (every use in the project, by `Holding` and the
  field's key), a parameter (its uses in each declaration of the family), a return
  type (each call's result).
- **Two goldens changed:** `change_type_field_simple` changed `int` to `long` beside
  `total = total + amount`, which is the overflow change itself; its use is now a
  copy (`total = amount`). `change_type_refused_uses` (to `String`) is refused by the
  rule before the gate; its fragment names the use.

## Notes

- Seven probes red first where they should be (five "behavior changed"), then green:
  overflow, division, `char` printing, a parameter's division, a printed result;
  a comparison and a widening copy still apply.
- `hand:` none. `ProbeTest`: 59 cases.

## Measured

All Java through jrefactor (`create_file` of `PrimitiveUses`, `add_declaration`,
`replace_text`); probes by one `bin/jrefactor @file` batch.

## Follow-ups

- Lambdas that implement a changed parameter are not checked for uses (the family's
  declarations are).
