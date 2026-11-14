---
order: 141
item: 148
status: Done
---

## Item

`remove_parameter` `value` with a call in it (`order.customer()`), declared first in the body as a local (`docs/sessions/generators-sketch.md`, slice 4, increment 2)

## Status

Done 2026-11-13 (`Signatures.removeWithExpression`, `ParameterValue.InMethod.acts` and `readsFirst`, the matcher's calls: the same method, or one overriding it on a receiver; a method of the method's own object only on a call on `this`. Refused: an argument after the value at a call that is not fixed, since the value's call would now run after it; an argument the value reads that is not fixed and is read after one of the value's calls; a constructor. Reported through `Exceptional`, since a call may throw)

## Evidence

The sketch; `docs/sessions/remove-parameter-value-call.md`
