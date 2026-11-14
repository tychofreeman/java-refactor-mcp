---
order: 341
item: 351
status: Done
---

## Item

The multi-file "apply edits, fix imports, gate" loop was `MemberMove.gate` (twelve callers, most not member moves) plus copies in `SubclassToStrategy`, `InlineClass`, `ExtractType`, `SignatureChange.apply` and `TypeParameters` (three), which differed: `MemberMove.gate` dropped the import notes, read each file from disk even when its text was given (an eager `getOrDefault`, also in `PullUp` and `PushDown`), sorted before `TextEdit.apply`, and did not skip a file with no edits (issue #497, code review)

## Status

Done 2026-11-14: `Edits.gatedFiles`, next to `Edits.gated`, returns `WrittenFiles` (texts, notes, stubs); every caller appends its `said()`, so `move`, `pull_up`, `push_down`, `inline`, `fold`, `encapsulate_field`, `add_result`, `type_switch_to_method`, `subclass_to_strategy`, `inline` of a class, `extract_type` and `add_parameter` of a component now name the imports they added or removed, as single-file edits and the signature tools did (goldens `move_member_imports`, `type_switch_enum_method`). `Edits.textOf` reads lazily. `MoveType` and `MoveToPackage` keep their own. Default to reopen: the notes are reported, over returning them unreported

## Evidence

Issue #497; `docs/sessions/one-gated-files.md`
