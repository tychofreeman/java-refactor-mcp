# rename by Type.member without line

- **Item:** issue #613 (code-review track, friction log), roadmap item 500
- **Date:** 2026-11-15
- **Branch:** rename-qualified-no-line

## Plan

Route a `rename` without `line` through `Declarations.find`, as the other tools resolve
`Type.member`: find the declaration's name node, then call the existing `Rename.run` with its
line and column. Make it easy first: extract `Rename.run`'s file check (`javaFile`) so the new
entry shares it. Then the edit: `line` not required in the schema, `Tools.rename` routes.

## Notes

- read: `inspect` of `Qualified`, `Declarations.find`, `Rename.run`; `Tools.rename` and the
  `Def` with sed (Tools.java, not a guard-protected read here since the guards are not loaded).
- plan: goldens first (6 cases), red: five refused "rename needs line".
- `extract_function` of the file check into `javaFile`; `replace_text` for its doc comment;
  `add_declaration` of `Rename.run(project, file, name, newName)` and `declaredName`;
  `replace_text` to use `javaFile`'s result; `replace_declaration` of `Tools.rename`;
  `replace_text` twice for the `Def`.
- hand: golden files and docs (plain files).

## Measured

Not measured (the session-log hook is not loaded here). From the notes: 7 tool edits to Java,
0 by hand.

## Assessment

Through the tools. The plan held. A field declared with others in one statement (`int a, b;`)
is still refused by `Declarations.find` with wording about `replace_text`, which does not fit
`rename`; pass `line` there.

## Follow-ups

- `Declarations.find`'s refusal for a multi-fragment field names `replace_text`; for `rename` it
  could say to pass `line`.
