# rename by Type.field: one field of a shared statement

- **Item:** issue #642, roadmap item 509
- **Date:** 2026-11-15
- **Branch:** rename-field-fragment

## Plan

The refusal of `int a, b;` belongs to the one tool that replaces a declaration's text
(`replace_declaration`), not to finding the member. Move it from `Declarations.find` into
`Declarations.replace`, right after its `find` (so its order before `SafeDelete.check` is kept),
while green; then make `Rename.declaredName` pick the fragment named, not the first.

## Notes

- read: `inspect` of `Declarations.find`, `Rename.run`/`declaredName`, `SafeDelete.check`, the
  `Declarations.find` callers in `TypeParameters` (each refuses a field itself or wants a type).
- plan: the golden for rename was red first; set aside while the refusal moved (a refactoring), then
  put back, as CLAUDE.md asks.
- refusal: `insert_statements` with `position` (it takes `where`); `batch` `retry` fixed the step.
- No golden pinned the moved refusal: added `replace_declaration_refused_shared_field` (passes
  before and after).
- Java edits: 3 tool calls (a batch of 2 moving the refusal, a batch of `add_parameter` +
  `replace_statements`, one `replace_text` for the doc comment); none by hand.

## Measured

Not measured (no session-log hook in this session).

## Assessment

Through the tools. The other `Declarations.find` callers now see a shared field statement where
they had the refusal: `TypeParameters` refuses a field itself, `SafeDelete.deletedLater` only drops
uses inside it, and the method-only callers never asked for a field.

## Follow-ups

None.
