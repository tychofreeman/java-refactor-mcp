# ExtractType: one parse, classOrRecord, one "create a new type's file"

- **Item:** issue #518 (code review, group 4 of #532).
- **Date:** 2026-11-14
- **Branch:** extract-type-sibling

## Plan

Pure consolidation, the goldens as the check. Move `ExtractSupertype.create` to `Edits` (as
`newFile`, with the refusal's last sentence a parameter, since the two classes word it differently),
then `fold` it over ExtractType's three copies. `Opened.packageHeader` for the header. Pass the
`Opened` that `run` parses into `parameters` and `fields`; `fields` asks `classOrRecord`.

## Notes

- plan: `move` of `ExtractSupertype.create` to `Edits`, then `rename` and `add_parameter` `fix`.
- refusal: `rename` in the batch after the `move` needed `line`, which the move had just decided
  (logged by one-label already); sent the move alone, then the rest as a batch.
- refusal: `fold` of `Edits.newFile` found no match: the copies passed `Imports.fix(...).text()`
  inline. `extract_variable` of it as `fixed` at each site, then `fold` in the same batch: 3 folded.
- plan: `Opened.packageHeader()` and `packageHeader(String)` by `add_declaration`; `fold` of the
  static one reached `Skeleton`; `replace_statements` for the four `String pkg = ...` declarations,
  `replace_expression` in `Stubs` and `MoveType` (no null test there, so `fold` did not match).
- refusal: `inline` of the `pkg` locals: their initializer is a call and would move past
  `Modifier.isPublic(...)`. Kept the locals.
- refusal: a batch line after an earlier step rewrote it must be `"$1.start"`; and
  `replace_expression` takes `expression`, not `old`. Both refusals said so.
- plan: `extract_variable` of `run`'s `Opened.of(...)`, `add_parameter` `opened` to `fields` and
  `parameters` (value `opened`), deleting their own parses in the same batch; then
  `replace_statements` of the record loop with `classOrRecord`.
- hand: the roadmap row was inserted with `sed` (docs).

## Measured

Not measured (no session-log hook; the CLI runs are in `JREFACTOR_LOG`). From the notes: every
Java change through `bin/jrefactor` (8 calls, 5 of them batches).

## Assessment

Through the tools. `fold` did the copy-removal once `extract_variable` aligned the shape: the
"extract, then fold" sequence CLAUDE.md suggests composed as intended.

## Follow-ups

- ExtractSupertype passes the same refusal sentence twice; a constant would do.
- Package names are looked up by hand in `MoveToPackage`, `MoveType`, `Stubs`, `Edits`, `Headers`:
  #511.
