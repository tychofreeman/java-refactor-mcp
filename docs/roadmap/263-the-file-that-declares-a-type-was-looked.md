---
order: 253
item: 263
status: Done
---

## Item

The file that declares a type was looked up seven ways; `TypeSwitch.fileOf` matched a path suffix over every file, so `inline` of a class field took `a/com/example/Address.java` for `com.example.Address` and refused, and `move` with `to=com.example.shop.Tax` refused "several files declare Tax" naming the decoy `a/com/example/shop/Tax.java` (issue #482, code review)

## Status

Done 2026-11-13: `SourceProject.fileDeclaring(qualified)` and `fileOf(ITypeBinding)` (by the implied path), `Names.outermost`; `TypeSwitch.fileAround`, `Stubs.Round.sourceOf`, `ByLine.topLevel` and `MoveMember.outermost` inlined into them; `Signatures.projects`, `Imports.own`, `Rename`, `Jdt.staticImported`, `ChangeSignature.referenced` (now with the local and anonymous check), `Fold`, `MoveToPackage` call them. `move` takes a qualified `to` as the import where a simple name is ambiguous. `inspect`'s package uses `packageOf`; its looser file rule is documented. Goldens `inline_class_same_suffix`, `move_member_qualified_same_suffix`

## Evidence

Issue #482; `docs/sessions/file-declaring-type.md`
