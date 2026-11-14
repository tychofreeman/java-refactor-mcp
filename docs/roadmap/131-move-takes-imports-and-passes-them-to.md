---
order: 124
item: 131
status: Done
---

## Item

`move` takes `imports` and passes them to every route that fixes imports (`move_member`'s gate fixes with none, yet its refusal says to pass them); an ambiguous name prefers the class in the project's own sources over a library's (`shop.order.Order` over `org.junit.jupiter.api.Order`)

## Status

Done 2026-11-06: `move` and `move_member` take `imports`; `Imports.chosen`'s third rule takes the project's own class

## Evidence

A/B check of the merged list (2026-11-06, `docs/sessions/ab-merged-tools.md`): all three `move` calls of one run refused, the last for `imports`, which the refusal before had asked for
