---
order: 17
item: 25
status: Done
---

## Item

`pull_up` and `push_down` for methods (and fields): move a member to a supertype or to its subtypes, with the type-check gate behind it

## Status

Done (2026-11-04): `pull_up` and `push_down` for methods, fields and overloads (`docs/sessions/pull-up.md`, `docs/sessions/push-down.md`, `docs/sessions/fields-pull-push.md`, `docs/sessions/overloads-pull-push.md`)

## Evidence

The user asked, 2026-11-03; no friction entry yet, so it waits for one. Also worth a look for gorefactor (Go has embedding, not inheritance: the nearest is moving a method between a struct and the one it embeds) and the TypeScript port
