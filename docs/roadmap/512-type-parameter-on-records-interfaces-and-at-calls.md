---
order: 502
item: 512
status: Done
---

## Item

`add_parameter` with `type_parameter`: a record or an interface as the target, and an explicit type argument (`Lists.<String>first(x)`) at a call that would otherwise infer another type (issue #375; increment 8 of `docs/sessions/generics-sketch.md`)

## Status

Done 2026-11-15, in two PRs. Part 1: a record or an interface as the class, a record component in `members`, a method of a record (`TypeParameters`; `CallTargets` places a record's implicit accessor and canonical constructor by the record). Part 2: a call that would infer another type is given `value` as its type argument (`TypeParameters.typeArgument`), with the qualifier Java requires: the class for a static method, `this` for a method of the class around the call.

Defaults chosen (not settled by the sketch; the smallest, to reopen): a method reference, a call that already has type arguments, and an unqualified call of an outer class's method stay refused with the inference wording, rather than writing `Lists::<String>first`, inserting among existing type arguments, or `Outer.this.<String>m(...)`. The calls are written in a second gated round after the declarations, as `fullDiamonds` writes diamonds out

## Evidence

Issue #375, left out of roadmap #156-#158 on 2026-11-13; the owner asked for it on 2026-11-15
