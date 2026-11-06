# add_declaration with implement

- **Item:** roadmap #123, the first of its three parts (the user, 2026-11-06: options on two tools;
  over three new tools, and over the two member operations without surround).
- **Date:** 2026-11-06
- **Branch:** add-declaration-implement

## Plan

`add_declaration` with `implement` in place of `content`: `*` adds a "not implemented" stub for
every abstract method the type's supertypes leave unimplemented (`Skeleton.unimplemented`, as
`create_file` with `declaration` does); a method's name (`describe`, `Base.describe`,
`describe(int)`) adds that one, found in the supertypes (`Object` included): abstract, a stub;
concrete, an override that calls `super` (an interface's default, `I.super`), so behavior is
unchanged until the model edits it. Refused, naming what it can implement: a name no supertype
declares, a final, static or private method, several overloads without parameter types, and `*`
with nothing left.

Derivation: goldens first; a class `Implement` (`create_file` with `declaration`, members by
`add_declaration`), `Skeleton.stub`/`parameters` widened to package access; the branch in
`Declarations.add` (`replace_text`); the `Def` argument and description.

## Notes

- read: `Skeleton`'s outline, `stub`, `parameters`; `Declarations.add`; the `Def`.
- `Implement` began as `create_file` with `declaration`, its members by one `add_declaration`.
- refusal: that `add_declaration`, for `Modifier` and `Path` (three candidates each, nothing to
  choose by in a new file); the refusal named the `imports`; resent with them. While resending I
  removed a line of mine that would have added null to an `ArrayDeque`.
- bulk: `Implement`'s members (142 lines, sent twice): new behavior.
- All four goldens passed on the first run after the wiring.

## Measured

Through jrefactor: 3 batches (one refused, then resent) and the docs; by hand the goldens
(resources), one roadmap cell by script, this record.

## Assessment

Through the tools, at the intended level: the class from its declaration, members added. The
import refusal in a new file is the one #105 cannot help with (no imports to choose by); the
refusal said exactly what to send.

## Follow-ups

- #123's other parts: `constructor` on `add_declaration`, `surround` on `replace_statements`.
