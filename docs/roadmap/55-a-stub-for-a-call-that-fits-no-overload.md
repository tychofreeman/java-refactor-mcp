---
order: 48
item: 55
status: Done
---

## Item

A stub for a call that fits no overload of a project method: `add_declaration` of a test calling `add("apple", quantity)` on a `Cart` with only `add(String)` was refused, where a new name would have got a stub

## Status

Done

## Evidence

Friction log 2026-11-03 (`docs/sessions/cli-cache.md`). `Stubs.find` handles `IProblem.ParameterMismatch` as an undefined method. Record: `docs/sessions/stub-overload.md`
