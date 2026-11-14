---
order: 252
item: 262
status: Done
---

## Item

`change_type` of a field neither parsed `type` first (`long)` got a refusal about a use) nor checked `[]` written after the field's name (`int counts[]` to `long[]` became `long[][]`, which type-checked when only `counts.length` read it); the new parameter's checks and the return type's preconditions were written out three and two times (issue #492, code review)

## Status

Done 2026-11-13 (`ChangeType.run`: `Signatures.parsesAs` first, and a refusal for a fragment's `extraDimensions`, as for a parameter; `Signatures.checkNewParameter` (from `addParameter`, `addComponent`, `EnumField`) and `Signatures.requireReturnType` (from `changeType` and `ChangeSignature`) by `extract_function` and `fold`; `TypeParameters.namesMethod` for its two routings. Goldens `change_type_refused_field_dimensions`, `_field_type_syntax`. Default to reopen: refuse the `[]` after the name, over moving it to the type)

## Evidence

Issue #492; `docs/sessions/fix-field-change-type.md`
