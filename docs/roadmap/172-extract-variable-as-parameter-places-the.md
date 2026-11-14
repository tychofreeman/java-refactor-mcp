---
order: 165
item: 172
status: Done
---

## Item

`extract_variable` `as=parameter` places the new parameter right after the last parameter the expression reads, so later arguments with effects run after it, in place of #161's refusal

## Status

Done 2026-11-13 (`Introduce.parameter` passes `before` to `Signatures.addParameter`; `perCall` checks only the arguments before the new place; an expression that calls or may throw stays last; the user's choice, "the new parameter goes right after the last one the expression reads" above)

## Evidence

The owner's decision; `docs/sessions/parameter-after-last-read.md`
