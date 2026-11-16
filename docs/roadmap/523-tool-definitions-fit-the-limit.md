---
order: 513
item: 523
status: Open
---

## Item

Trim every tool definition still in `ServerTest.DEFINITION_OVER` to the 1,700-character limit
(`ServerTest.definitionsStaySmall`), as #675 did for `move` (the owner, 2026-11-16)

## Status

Open (2026-11-16): 13 of the 14 fit, and left `DEFINITION_OVER`: `change_signature` (2,596 to
1,693), `insert_statements` (2,518 to 1,690), `remove_parameter` (2,349 to 1,686), `change_type`
(2,257 to 1,692), `type_switch_to_method` (2,154 to 1,686), `replace_statements` (2,114 to
1,596), `extract_type` (2,048 to 1,688), `extract_variable` (2,029 to 1,564), `fold` (1,897 to
1,685), `extract_function` (1,852 to 1,679), `batch` (1,801 to 1,684), `add_declaration` (1,776
to 1,589), `inline` (1,760 to 1,663). Each kept one example per route; what each lost is in
`docs/sessions/tool-descriptions-trim.md`. `add_parameter` (3,374 to 3,338, through the shared
arguments only) cannot fit: with every example cut it is still 1,902 characters, since its 13
arguments' schema alone is 897. Its text is unchanged, and its entry stays at 3,338. Left for the
owner: keep it over the limit with a trimmed text (2,396, every route's example kept), or take a
route out of the tool (the type parameter one, say, to its own tool).

## Evidence

`DEFINITION_OVER` (#420) listed these 14 tools; the owner asked for them to fit
