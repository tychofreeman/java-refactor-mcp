# Imports chosen by what the package imports

- **Item:** the friction-log entry of 2026-11-06 (the ambiguous-import rule and new files) and
  four refusals the same day, each in a file just made from `declaration`, each resending the
  whole member text (120 to 240 lines): `Modifier` and `Path` (#123 `Implement`), `Expression`
  (#124), `UnionType` (#123 `Surround`), `Name` and `QualifiedName` (#127).
- **Date:** 2026-11-06
- **Branch:** package-import-evidence

## Plan

An ambiguous simple name that the file's own evidence does not decide (its earlier imports, the
packages it imports from) takes the candidate that the other files of its package import most,
when one does so strictly more than the others, and says so (`chose org.eclipse.jdt.core.dom.Name
over ...: 12 of this package's files import it`). The project's-own-class rule (#131) comes
after it. Siblings are read only when a name is ambiguous.

Derivation: golden first (a file with no imports, a sibling importing `java.sql.Date`); in
`Imports.chosen`, the rule, with the siblings' imports counted by a helper.

## Notes

- read: `Imports.chosen`.
- One batch for the rule; counting this package's imports (`inspect` `pattern`) showed it would
  have decided all four refusals (`org.eclipse.jdt.core.dom.Modifier`, `Expression`, `Name`,
  `QualifiedName`, `UnionType`, `java.nio.file.Path` are each imported by more files than their
  rivals), and that the `Pattern` mistake (friction log, 2026-11-06) came from the packages rule
  running first: the files here import `java.util.regex.Pattern`, none JDT's.
- plan: the new rule moved before the packages rule; `move_statement` refused the swap (both
  write `pick`), rightly, since the swap changes behavior; done as an edit (`replace_statements`).
- No golden pins the order (JDT is not on the goldens' classpath); the count above is the check.

## Measured

Through jrefactor: one batch, one refused refactoring then an edit, one doc-comment edit; the
docs by script; by hand the golden (resources) and this record.

## Assessment

Through the tools; the refusal of `move_statement` was correct and pointed at the edit.

## Follow-ups

- A golden for the order, with a classpath that has two packages declaring one name.
