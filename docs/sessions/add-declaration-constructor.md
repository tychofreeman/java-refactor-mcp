# add_declaration with constructor

- **Item:** roadmap #123, part 2 of 3 (constructor from fields).
- **Date:** 2026-11-06
- **Branch:** add-declaration-constructor

## Plan

`add_declaration` with `constructor` (field names, in order) in place of `content`: a
constructor of the type with a parameter per field, of the field's type as written, assigning
each (`this.a = a;`), with the class's access (public for a public class). Refused, naming the
fields it can take: a name that is not a field of the type, a static field, a final field that
has a value, an interface, enum or record (a record has its canonical constructor). A stub
constructor of the same parameter types is replaced, as for any member.

Derivation: goldens first; `FromFields.constructor` (syntax only: the type and its fields) in a
new file from `declaration`; the branch in `Declarations.add`; the `Def` argument.

## Notes

- read: `Declarations.owner` (widened to package access with its `Owner` record, to reuse it).
- One batch of nine steps (the class from `declaration`, its member, the wiring, the `Def`); no
  refusals; `verify` passed first time.

## Measured

Through jrefactor: one batch and the docs; by hand the goldens (resources) and this record.

## Assessment

Through the tools, as planned.

## Follow-ups

- #123 part 3: `surround` on `replace_statements`.
