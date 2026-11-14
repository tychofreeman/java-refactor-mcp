---
order: 256
item: 266
status: Done
---

## Item

Supertype walks written about twenty times (issue #495, code review): worklists over `getSuperclass()` and `getInterfaces()` with a seen set by key, by erasure key or none (a diamond interface visited twice), the start type in or out, DFS or BFS, and subtype tests by key or by qualified name (two local classes both have the name "")

## Status

Done 2026-11-14: `Hierarchy.supertypes(t, self)` (breadth-first, nearest first, each once by erasure key), `overridden(m[, from])`, `concreteOverridden`, `isSubtype(t, of | key)` (by erasure key; every reference type is an `Object`) and `isA(t, qualified)` (by name, for library types). `CallTargets`, `Signatures`, `ParameterValue`, `InlineFunction`, `Build`, `ConvertLoop`, `SafeDelete`, `MoveType` and `PullUp` helpers inlined or moved into it; the walks of `Extraction`, `Fold`, `Implement`, `Overrides`, `Rename`, `Stubs`, `ConvertLambda`, `EnumField`, `ExtractVariable`, `Skeleton` and `LoopToStream` loop over it. Behavior: `create_file` `declaration` and `implement` stubs come nearest supertype first (`Skeleton` walked depth-first: golden `create_file_declaration_nearest_first`); `HierarchyTest` pins the dedup, the order and identity by key. Left: superclass-only chains and direct-supertype checks (not walks), `Jdt.completeMembers`, `LoopToStream.elementOf` (needs the type arguments), and the downward "does anything override this" lookups (`InlineFunction.refuseOverridden`, `Fold.overrider`, `AddResult`, `MoveMember`, `Extraction`)

## Evidence

Issue #495; `docs/sessions/hierarchy-helper.md`
