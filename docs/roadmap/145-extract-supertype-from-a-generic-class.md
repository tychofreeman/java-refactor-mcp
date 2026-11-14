---
order: 138
item: 145
status: Done
---

## Item

`extract_supertype` from a generic class, `kind=class`: the new superclass declares the type parameters its members and the old superclass's type use, with those their bounds need; the class extends it with their names, and the members move as `pull_up` moves them

## Status

Done 2026-11-13 (`ExtractSupertype.run`; `PullUp` unchanged: it finds the supertype by erasure and moves text whole); probe `extract_supertype_generic_class`

## Evidence

`docs/sessions/generics-sketch.md` (increment 2)
