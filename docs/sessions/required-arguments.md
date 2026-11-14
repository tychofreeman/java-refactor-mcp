# One check for required arguments

- **Item:** issue #521 (code review, core #6), the owner's option (a) of 2026-11-14; roadmap #496
- **Date:** 2026-11-14
- **Branch:** required-arguments

## Plan

`Tools.Def` keeps its `List<Arg>`; `Args.check` takes it and refuses a missing required one in
`Arg.missing`'s words; then the hand-written checks go. Test first: `RequiredArgsTest` calls every
`Def` (and alias) without each required argument and expects the wording.

## Notes

- read: `Arg`, `Args.check`, `Tools.Def`, `Tools.call`, the routers (`move`, `inline` call their
  target's handler directly, so only their own schema is checked), each site the issue lists.
- read: the schemas of every tool (`ToolSchemasTest` with `-Djrefactor.schemas.out`), and each
  golden's arguments against them: no golden omits an argument its tool marks required.
- red: `RequiredArgsTest` (a stub `Def.arguments()` declared by `create_file`).
- bulk: `replace_declaration` of `Tools.Def` with `replace_text` of its test caller in one batch;
  `add_declaration` of `Arg.missing`, `replace_declaration` of `Args.check`.
- bulk: one batch of 15 steps deleting the hand checks (`replace_statements`), and
  `Primitives.line` made `args.integer(key)` then inlined into its 7 calls.
- refusal: the first `replace_declaration` of `Def` (its test caller); resent in a batch.
- golden: `replace_parameter_alias_refused_no_value` was the one golden whose text changed.

## Measured

Not measured (no session-log hook loaded). From the notes: 7 tool calls that wrote Java, none by
hand; goldens and docs with ordinary file tools.

## Assessment

Every Java change went through the tools; the batch of 15 deletions was planned from one read.

## Follow-ups

- `Args.required` still refuses an empty string in its old words ("rename needs name."); it could
  use `Arg.missing` if `Args` kept the tool's arguments.
