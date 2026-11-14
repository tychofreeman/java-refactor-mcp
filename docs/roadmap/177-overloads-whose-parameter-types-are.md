---
order: 170
item: 177
status: Done
---

## Item

Overloads whose parameter types are written alike but erase differently (`<T> T raise(T t)`, `<T extends Throwable> T raise(T t)`) can be named, and the refusal for several overloads suggests a name that works (issue #408, from the Commons Lang stress run)

## Status

Done 2026-11-13 (`Declarations.find` matches written parameter types, then erased ones (`erasedTypes`); `methodLabel` gives the erased form for a method written like another, in the refusal and in `ByLine.named`; "Decided 2026-11-13: overloads written alike are named by erasure" above)

## Evidence

`docs/sessions/stress-new-options.md`; `docs/sessions/fix-overload-names.md`
