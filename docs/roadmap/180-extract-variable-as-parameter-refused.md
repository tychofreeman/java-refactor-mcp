---
order: 173
item: 180
status: Done
---

## Item

`extract_variable` `as=parameter` refused every method with generic parameter types ("no member apply with parameters (Function, Object)"; issue #406, from the stress run)

## Status

Done 2026-11-13 (`Introduce.parameter` names the method by its declaration's label, `MemberMove.label`, as `ByLine.named` does, in place of `Signatures.label`'s erasures; a varargs method now reaches `MethodFamily`'s own refusal)

## Evidence

Issue #406; `docs/sessions/fix-parameter-generic-types.md`
