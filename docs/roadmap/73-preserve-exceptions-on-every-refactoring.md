---
order: 66
item: 73
status: Done
---

## Item

`preserve_exceptions` on every refactoring, and a separate section in the result naming each change to exceptional behavior (a dereference moved past a guard, a call declaring a checked exception moved, an expression moved into or out of a `try`)

## Status

Done (2026-11-04), local session: the flag, the result's own part, and three tools' first cases; record in `docs/sessions/preserve-exceptions.md`

## Evidence

The user's decision, 2026-11-04 (Decisions, and open question 0: one flag, a separate field). First case: `move_member`'s null receiver
