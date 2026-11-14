---
order: 62
item: 69
status: Done
---

## Item

`pull_up` refuses a method that, in the supertype, overrides one the supertype inherits: every other subclass then runs the pulled body (`Other.name()` returned "root", then "sub")

## Status

Done (2026-11-04), local session; record in `docs/sessions/pull-up-inherited-override.md`

## Evidence

Found by a probe after `docs/sessions/stress-new-tools.md`; applied and type-checked. `PullUp`, `MemberMove`
