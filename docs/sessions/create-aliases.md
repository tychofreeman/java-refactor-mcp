# The `create_*` aliases

- **Item:** roadmap #166 (order 159), the generators sketch's increment 8 without
  `replace_parameter` (#160, another session's): `create_loop`, `create_call`, `create_guard`.
- **Date:** 2026-11-13
- **Branch:** create-aliases

## Plan

Three `Def`s outside `Tools.ALL`, in `Tools.ALIASES`, as `replace_parameter` is: flat
arguments for gorefactor's contract, each handler `insertOne`, which builds one `build` entry
from the arguments it names (`over` becomes `loop`) and calls `Primitives.insertStatements`
with the placement arguments. `ALIASES` passes ten entries, so `Map.of` becomes
`Map.ofEntries`.

## Notes

- `read:` `Tools.ALIASES`, `REPLACE_PARAMETER`, `Tools.call`, gorefactor's `docs/edits.md`
  (`create_loop` there is planned with `op`, `field` and `function`; none of those here).
- `hand:` the four golden cases (resource files), with the shell, as the brief allows.
- Through the tools: one batch of three steps (`replace_text` of `ALIASES`, two
  `add_declaration`); `add_declaration` placed the three fields before `ALIASES`, which reads
  them (`FieldOrder`), so no forward reference.

## Measured

Not measured: the session-log hook is not loaded in this session.

## Assessment

Through the tools; no refusal. The plan held.

## Follow-ups

- An A/B arm that publishes these (the sketch's question 2, option c) is the measurement of
  whether a name is found where an option is not; not run (issue #376).
