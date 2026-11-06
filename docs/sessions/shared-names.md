# One name per operation: extract_function, and method_to_function

- **Item:** roadmap #120, gorefactor's `docs/parity.md` section 2 (the user's rule, 2026-11-05:
  the more generic name wins, the others kept as aliases).
- **Date:** 2026-11-06
- **Branch:** shared-names

## Plan

`extract_method` is published as `extract_function`; `extract_method` stays a name `batch` and
the CLI accept (`Tools.ALIASES`, consulted by `Tools.named`), so recipes and old session logs
still work. `method_to_function` (Go's name) is an alias, not published, for `move_member` with
`to` the method's own class, taken from `name`. The session report counts an alias as its tool.

Not published: an alias would add a tool to the list the client loads (and the 50 limit), and
the parity rule keeps the list to the shared names.

Derivation: `rename` of the `Def` constant (`EXTRACT_METHOD` to `EXTRACT_FUNCTION`), then
`replace_text` of its name; `add_declaration` of `METHOD_TO_FUNCTION` and `ALIASES`;
`replace_statements` in `named`; the report's `kinds`. Goldens: one `extract_method` case
renamed to the new name, the others keep the alias tested; a `method_to_function` case.

## Notes

- read: `Tools.named`, `Tools.ALL`, the `MOVE_MEMBER` `Def`, `SessionReport.kinds`.
- `rename` of the constant, then one batch of four steps; no refusals.
- The eval instructions (`eval-tasks/claude-md/mcp.md`) and `examples/CLAUDE.md` name the new
  tool; a later A/B run measures them with it (the baselines' runs used `extract_method`).
- Goldens: `extract_method_return` now calls `extract_function`; the other `extract_method_*`
  cases test the alias; `method_to_function_alias` and `method_to_function_refused_name`.

## Measured

Through jrefactor: 1 refactoring (`rename`), 3 edit batches (13 steps) and the docs; no
refusals; by hand the goldens (resources, by `cp` and `sed`) and this record.

## Assessment

Through the tools. A renamed published tool is a change for MCP clients that call it by its old
name: the alias covers `batch` and the CLI, not a direct MCP call.

## Follow-ups

- #121 (`move`) and #122 (`inline`) use the same `ALIASES`.
