---
order: 482
item: 492
status: Done
---

## Item

One parser for `Type.member(params)`: `Qualified.parse` splits at the last dot before the parameter list, so `Outer.Inner.m` and `m(Map.Entry, Map<K, List<V>>, int...)` read alike in every tool; `Qualified.of`, `fold`, `implement`, `test_for` and `Declarations.memberName` read names through it

## Status

Done (2026-11-14): `Qualified.parse`, ported in `Qualified.of`, `Fold`, `Implement`, `TestFor`, `Tools.methodToFunction` and `Declarations.memberName`; goldens `pull_up_refused_member_type`, `fold_nested_type`, `add_declaration_implement_member_type`, `create_file_test_for_uppercase_method`, unit test `QualifiedTest`

## Evidence

The owner's choice of 2026-11-14 on the follow-ups of #501 (`docs/sessions/one-label.md`): four splitters disagreed. `pull_up` of `Outer.Sub.k` refused "Outer has no superclass"; `fold` of `Price.Tax.withTax` wrote `Tax.withTax` where `Tax` does not resolve; `implement=Shapes.Shape.area` found no supertype; `test_for=Request.GET` found no type `Request.GET`
