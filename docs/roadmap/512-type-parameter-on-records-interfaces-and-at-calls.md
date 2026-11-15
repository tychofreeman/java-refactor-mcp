---
order: 502
item: 512
status: Open
---

## Item

`add_parameter` with `type_parameter`: a record or an interface as the target, and an explicit type argument (`Lists.<String>first(x)`) at a call that would otherwise infer another type (issue #375; increment 8 of `docs/sessions/generics-sketch.md`)

## Status

Part 1 done 2026-11-15: a record or an interface as the class, a record component in `members`, a method of a record (`TypeParameters`; `CallTargets` places a record's implicit accessor and canonical constructor by the record). Part 2, the explicit type argument at a call, is open

## Evidence

Issue #375, left out of roadmap #156-#158 on 2026-11-13; the owner asked for it on 2026-11-15
