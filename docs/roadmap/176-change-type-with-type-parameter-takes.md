---
order: 169
item: 176
status: Done
---

## Item

`change_type` with `type_parameter` takes `line` (and `column`) for a method, as the contract said; it refused, needing `name` (issue #407, from the Commons Lang stress run)

## Status

Done 2026-11-13 (`Tools.changeType` sends `line` through `ByLine.named` before `TypeParameters.bound`, as the parameter and return forms do)

## Evidence

`docs/sessions/stress-new-options.md`; `docs/sessions/fix-type-parameter-line.md`
