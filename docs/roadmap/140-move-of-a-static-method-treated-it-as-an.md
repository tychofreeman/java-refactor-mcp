---
order: 133
item: 140
status: Done
---

## Item

`move` of a static method treated it as an instance one: refused a call of itself and a parameter named `receiver` (issue #356)

## Status

Done 2026-11-11 (`MoveMember.checkBody`: both refusals for an instance method only; a static method's call of itself moves unqualified, `Walk.depth(...)` too)

## Evidence

Friction log 2026-11-10 and 2026-11-11; `docs/sessions/move-recursive-static.md`
