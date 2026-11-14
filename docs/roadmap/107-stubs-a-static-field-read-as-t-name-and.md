---
order: 100
item: 107
status: Done
---

## Item

Stubs: a static field read as `T.NAME` (the class, and the field typed from its use), and a stub replaced by a declaration whose parameters have the same erasure

## Status

Done 2026-11-06: `Stubs.Round.field` (`// not implemented` marks it), `Declarations.stubFor` compares erased types

## Evidence

Friction triage: `SessionLog.MAX_RESULT` (2026-11-03), and `allPure(List)` clashing with `allPure(List<?>)` in a batch (2026-11-02); both reproduced on 2026-11-05
