---
order: 203
item: 211
status: Done
---

## Item

`invert_if` whose else branch is unbraced and ends in an if without an else (`else for (...) if (d) z();`): the swapped `else` attached to that inner if, which type-checked and ran the old then branch per element

## Status

Done 2026-11-13: `InvertIf.thenText` puts such a branch in braces (`endsInOpenIf` follows `for`, enhanced `for`, `while`, a label and an if's else). Default to reopen (the owner said take the smallest): braces only where needed, over braces always (it would change every unbraced else) and over refusing (the model would add the braces by hand)

## Evidence

Issue #470 (code review); probe `invert_if_dangling_else`
