# One rule for a new name: Precondition.identifier

- **Item:** issue #494 (code review of 2026-11-13, extract-inline #17), group 2 of tracking issue #532
- **Date:** 2026-11-13
- **Branch:** fix-identifier-names

## Plan

Reproduce with `extract_function name=a.b` and `add_parameter parameter=x.y`. Then one
`Precondition.identifier(name, what)` (identifier, not keyword), and every site that checks one
name calls it, in one batch.

## Notes

- plan: both goldens failed as the issue said ("wrote code that does not parse", and compile
  errors for `int x.y`).
- read: `grep` for `SourceVersion.is` across the sources to list the sites (more than the review's:
  its line numbers had moved).
- edit: one batch of ten steps from a file (`bin/jrefactor - < file`): `add_declaration` of
  `Precondition.identifier`, nine `replace_text`; the tool removed the unused imports.
- Left alone: `MoveToPackage` (a package name takes dots), `Stubs` (an internal derived name),
  `ConvertLambda` (a keyword check only, for a generated name).
- hand: the goldens and the docs.

## Measured

Not measured: the session-log hook is not loaded here. All Java through the tools.

## Assessment

Through the tools, in one batch.

## Follow-ups

None.
