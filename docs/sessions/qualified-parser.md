# One parser for Type.member(params)

- **Item:** roadmap #492, the owner's choice of 2026-11-14 on the follow-ups of #501
  (`docs/sessions/one-label.md`), option (a): extend `Qualified` and port the four splitters.
- **Date:** 2026-11-14
- **Branch:** qualified-parser

## Plan

`Qualified` becomes the one reader of `Type.member(params)`: split at the last dot before the
parameter list (`parse`), with `name()` and `params()` (moving `Declarations.topLevelCommas` in).
Then port each splitter, one red golden each: `Qualified.of` (first dot; `pull_up` of a nested
member), `Fold` (last two segments; a static method of a nested class), `Implement` (last dot,
owner compared by simple name; an interface nested in a class), `TestFor` (case guess; a method
named in upper case). `Declarations.memberName` reads through it too, so `Declarations.find` and
`MemberMove.member` share it.

## Notes

- read: `inspect` with `pattern` for the splitters (`lastIndexOf('.')`, `indexOf('.')`), `names`
  for `Declarations.memberName`, `Opened.topLevel`, `Nodes.typeAround`, `Extract.typeName`.
- red: `pull_up_refused_member_type` ("Outer has no superclass to pull up into"),
  `fold_nested_type` ("Tax cannot be resolved"), `add_declaration_implement_member_type` (no
  supertype declares `Shapes.Shape.area`), `create_file_test_for_uppercase_method` (no type
  `Request.GET`): each failed on `main`.
- plan: a fifth splitter, `Tools.methodToFunction` (first dot), ported too; `Rename.Query` keeps
  its own (it takes `local x`, and no parameter list).
- refusal: `inspect` with a list of names refused the whole list for one wrong name; resent.
- refusal: `replace_text` took only one argument from stdin; sent as a batch with sections.
- refusal: `Declarations.typeLabels` is private (the gate); listed the top-level names inline.
- bulk: five batches, four single calls; `QualifiedTest` by `create_file`.

## Measured

Not measured: the session-log hook is not loaded here (the CLI log, `JREFACTOR_LOG`, is).
From the notes: no Java by hand; golden files are resource files, written as files.

## Assessment

Through the tools. `QualifiedTest` came after the code (the goldens were the red step).

## Follow-ups

- The tools behind `Opened.topLevel` refuse a member type; making them act on one is a default to
  reopen (`docs/design.md`, Decisions).
