# extract_struct: one shape

- **Item:** the user, 2026-11-06: "Go ahead with the one-shape version for extract_struct", after
  asking whether the three cases differ to a model (they do not: one intent, group members into a
  new type, every use following).
- **Date:** 2026-11-06
- **Branch:** extract-struct-one-shape

## Plan

Arguments `file`, `name`, `members`, `struct`, `as` (and `value_semantics`, `imports`): what is
grouped follows from what `name` is: a top-level class or record of `file` (its fields, or
components), else a method (its parameters). The old `parameters`/`fields` and
`parameter`/`field` stay accepted (renamed to `members` and `as` before the arguments are
checked, `Tools.ARG_ALIASES`), so recipes and old runs keep working; they are not published.

Derivation: goldens with the new names (the old ones now test the aliases); `Args.renamed`;
`Tools.call` applies the aliases; `ExtractStruct.run` routes, its old body renamed `parameters`
(`rename`); the `Def`'s arguments and description.

## Notes

- `rename` of `ExtractStruct.run` to `parameters` (2 sites); then one batch of six steps (the
  alias method, the alias step in `Tools.call`, `ARG_ALIASES`, the `Def`, the routing `run`, the
  old dispatch removed); every golden passed, the eleven old ones through the aliases.
- read: `Args`' outline, the `Def`, `ExtractStruct.run`'s first lines.

## Measured

`extract_struct` 2,198 characters to 1,913 (its arguments seven, from ten). Through jrefactor:
one refactoring, one batch, the contract; by hand the goldens (copies, resources) and this record.

## Assessment

Through the tools. Still above 1,700: three worked examples; one shape made room for them.

## Follow-ups

- The same look at `create_file` and `add_declaration` (modes by which argument is present).
