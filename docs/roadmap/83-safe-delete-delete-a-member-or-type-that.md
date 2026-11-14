---
order: 76
item: 83
status: Done
---

## Item

Safe delete: delete a member or type that nothing uses, refused with the uses when something does (gorefactor's `remove_dead_code`)

## Status

Done (2026-11-04), local session: `replace_declaration`'s delete, `SafeDelete`; record in `docs/sessions/safe-delete.md`

## Evidence

Recommendation 3 (2026-11-04). Perhaps an option on `replace_declaration`, which deletes with empty content and refuses a used declaration through the gate
