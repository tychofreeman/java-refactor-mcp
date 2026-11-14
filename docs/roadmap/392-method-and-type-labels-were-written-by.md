---
order: 382
item: 392
status: Done
---

## Item

Method and type labels were written by about ten formatters: `implement` listed `Base.describe(int,String)` (no spaces) where every other tool writes `describe(int, String)`, and `move` to a package reported a widened method as `Shop.price()` whatever its parameters; `Signatures.label`, `Skeleton.signature`, `TestFor.signature`, `Implement.label`, `Inspect.chain`, `Generated.key` and `ThrowsCallers.stopOf` each wrote their own (issue #501, code review)

## Status

Done 2026-11-14: `Names.methodLabel` (was `Signatures.label`, by `move`), `Names.signature`, `Names.sourceLabel` (`implement`'s lists, `create_file`'s stubs), `Names.typeLabelAt` (`inspect` names, `Generated`, `stop_at`); `MoveToPackage` names a method by `Names.methodLabel`. Goldens `move_type_to_package_widened_method_parameters`, `add_declaration_implement_refused_overloads`, `_unknown_lists` (each failed before), `NamesTest`. Open for the owner: one format for erased against written parameter types, and one parser for `Type.member(params)` (`Qualified`, `Fold`, `Implement`, `TestFor`)

## Evidence

Issue #501; `docs/sessions/one-label.md`
