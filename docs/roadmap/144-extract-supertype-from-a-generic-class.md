---
order: 137
item: 144
status: Done
---

## Item

`extract_supertype` from a generic class, `kind=interface`: the new interface declares the class's type parameters its signatures use, and those their bounds need, in the class's order, each as the class writes it; the class implements it with their names

## Status

Done 2026-11-13 (`ExtractSupertype.generics`; the owner chose the sketch's recommendations, slice 2 option (a), over all of the class's parameters always and over a `type_parameters` argument)

## Evidence

`docs/sessions/generics-sketch.md` (increment 1); the first follow-up of `docs/sessions/extract-supertype.md`; the old refusal named no fix
