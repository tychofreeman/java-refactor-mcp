# rename of a record refused as captured

- **Item:** found while splitting `Declarations` (#32, `docs/sessions/split-placement.md`): `rename`
  of the record `Place`, moved to its own file, was refused at every use ("would change what Spot at
  line 160 ... means: it would mean the type Spot, not the renamed one"), whatever the new name.
  The branch is named for the first guess (the use was a type argument); the cause is the record.
- **Date:** 2026-11-08
- **Branch:** rename-type-argument

## Plan

The change: `Rename.checkMeanings` knows a record's name declares it. JDT's
`SimpleName.isDeclaration()` is false for a `RecordDeclaration`'s name, so no renamed declaration
was found, and every name the rename wrote looked captured. Red: golden
`rename_type_in_type_argument` (a record used as `Map<Place, String>` in another file). Green: the
declaring-name test also takes an `AbstractTypeDeclaration`'s name, as it already took a
constructor's.

## Notes

- read: `Rename.checkMeanings` and the end of `renameType` (560-625, 690-740), from the stack.
- refusal: `replace_statements` at the comment's line 583: statements start on 584; resent there,
  and the old comment then deleted with `replace_text`.

## Measured

Not separated from the split's session; three jrefactor edits, no hand edits of Java.

## Assessment

Through the tools. The bug had been there since records were renamed at all; no golden renamed a
record a file used elsewhere.

## Follow-ups

- Other `isDeclaration()` calls may miss records the same way (`inspect pattern=isDeclaration`).
