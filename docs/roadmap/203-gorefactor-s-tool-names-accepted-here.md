---
order: 196
item: 203
status: Partly done
---

## Item

gorefactor's tool names accepted here: `introduce_parameter`, `parameterize`, `convert_params_to_object`, `add_field`, `merge_duplicate_function`, `remove_dead_code`, `prepare_switch_for_types`

## Status

Partly done 2026-11-15 (the user chose aliases here and options in gorefactor, go-refactor-mcp #634): `introduce_parameter`, `convert_params_to_object`, `remove_dead_code`, `prepare_switch_for_types` (`GoNames`, goldens `alias_*`). `parameterize` became a tool of its own (#508, the owner's choice of 2026-11-15). Left: `merge_duplicate_function` and `add_field` on a class (compositions, put off by the user)

## Evidence

Issue #372 (parity review): a recipe or batch written for one server is refused by the other
