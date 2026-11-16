---
order: 513
item: 523
status: Done
---

## Item

Trim every tool definition still in `ServerTest.DEFINITION_OVER` to the 1,700-character limit
(`ServerTest.definitionsStaySmall`), as #675 did for `move` (the owner, 2026-11-16)

## Status

Done (2026-11-16): 13 of the 14 fit, and left `DEFINITION_OVER`: `change_signature` (2,596 to
1,693), `insert_statements` (2,518 to 1,690), `remove_parameter` (2,349 to 1,686), `change_type`
(2,257 to 1,692), `type_switch_to_method` (2,154 to 1,686), `replace_statements` (2,114 to
1,596), `extract_type` (2,048 to 1,688), `extract_variable` (2,029 to 1,564), `fold` (1,897 to
1,685), `extract_function` (1,852 to 1,679), `batch` (1,801 to 1,684), `add_declaration` (1,776
to 1,589), `inline` (1,760 to 1,663). Each kept one example per route; what each lost is in
`docs/sessions/tool-descriptions-trim.md`. `add_parameter` is over by design (the owner,
2026-11-16; `docs/design.md`, Decisions): its 13 arguments' schema alone is 897 characters, so it
is 1,902 even with every example cut; it was trimmed to 2,396 with all five routes' examples kept
(3,374 before), its entry in `DEFINITION_OVER` lowered to match
(`docs/sessions/add-parameter-trim.md`).

## Evidence

`DEFINITION_OVER` (#420) listed these 14 tools; the owner asked for them to fit
