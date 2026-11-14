---
order: 360
item: 370
status: Done
---

## Item

`fold` found its method by name alone and took the first overload: with `twice(int)` and `twice(long)`, `fold Util.twice` folded `twice(int)` without a word, and `Util.twice(long)` was refused as no method (issue #486, code review)

## Status

Done 2026-11-13 (`Fold.method` asks `Declarations.find`, which takes parameter types and refuses several overloads with each one's name; `run` reads the type and method before the parentheses. Goldens `fold_refused_overloads`, `fold_overload_named`; `fold_left_overload` now names `Main.show(Object)`. The issue's other part, the third resolution in `overrider`, is left: a cost, not a wrong result)

## Evidence

Issue #486; `docs/sessions/fix-fold-overload.md`
