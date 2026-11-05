# One schema vocabulary for Tools (roadmap #97)

- **Item:** roadmap #97, from PR #161's review: the 36 tools' schemas were
  hand-written JSON, with the argument names written again in a `Set.of` beside
  each. The user's order of 2026-11-04 ("the items introduced by PR #161").
- **Date:** 2026-11-05
- **Branch:** schema-vocabulary

## Decisions (this session)

- **Builders, not shared descriptions.** A survey of the schemas: `file` has 10
  descriptions over 34 uses, `name` 16 over 20, `line` 10 over 11; only `imports`
  (12 uses) and the plain `file` (16) share text. The descriptions teach each
  tool's use, so they stay per tool. `Arg` gives typed builders and the two
  shared arguments, and each `Def` derives its schema, `required` and argument
  names from one list, so they cannot drift (they had not yet).
  Options it beat: keep the JSON and add a test that the `Set.of` matches the
  schema (no shorter, keeps 114 hand-written fields); one description per
  argument across tools (changes model-facing text the A/B runs measure).
- **The schemas stay byte-for-byte the same in meaning:** `ToolSchemasTest`
  (opt-in) writes every schema normalized; the dumps before and after are
  identical. Arguments with nested schemas (`batch`'s `steps`) use `Arg.json`.
- `Arg`'s component is `mandatory`: `required()` is the builder, and a record's
  accessor cannot share its name with another method.

## Plan

1. `ToolSchemasTest` and a dump of the schemas.
2. `Arg` (`create_file`) and a `Def` constructor from `List<Arg>`
   (`add_declaration`).
3. The conversion: a script read each schema and wrote its `Arg` list, sent as
   one batch of 36 `replace_text` steps through `bin/jrefactor`, check `end`.
4. A second dump, diffed with the first.

## Notes

- `bulk:` the conversion rewrote about 400 lines of `Tools` (existing schemas
  in a new form) through `replace_text` steps written by a script: no
  refactoring turns a JSON text block into builder calls.
- `refusal:` `create_file` of `ToolSchemasTest` with Jackson 2's packages (the
  project uses Jackson 3, `tools.jackson`); rewritten after `javap`.
- `refusal:` `create_file` of `Arg` with a component `required` and a method
  `required()`; renamed the component.
- The script's first pattern assumed 12 spaces of indentation (it is 10); it
  matched nothing and sent nothing.

## Measured

All Java through jrefactor (two `create_file`, one `add_declaration`, one batch
of 36 `replace_text`), two refusals answered by fixing the content.

## Assessment

- **Through the tools or around them:** through the edit tools; the content of
  the big edit came from a script, which is the honest description of how it
  was written.
- **Did the plan hold?** Yes.

## Follow-ups

- Splitting `Tools` (#32): the `Def`s could now move to files by kind.
