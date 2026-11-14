# One binding declaration-key helper

- **Item:** issue #496 (code review, tracking issue #532 group 3).
- **Date:** 2026-11-13
- **Branch:** binding-keys

## Plan

Confirm the suspected failure with golden cases (`move` of a generic static method,
`encapsulate_field` through a parameterized type); create `Bindings`, move one copy of the key
helper there, fold or inline the others, then swap the raw comparisons of a use with a
declaration.

## Notes

- red: `move_generic_static_method` refused by the gate ("id(String) is undefined for Util");
  `encapsulate_field_generic` refused ("Box<String>.value is not visible"). Confirmed.
- refactor: `create_file` Bindings, `move` NameCheck.key, `rename` declarationKey, `fold` (one
  match), delegates + `inline` for SafeDelete, Rename, MoveToPackage, Fold.
- decision: `ParameterValue.key` kept as `Bindings.erasedKey` (moved, renamed): whether its
  erasure is deliberate is the owner's (#500).
- refactor: `Bindings.same`; `LoopToStream.same` and `ConvertLoop.same` delegated and inlined.
- green: `replace_text` swaps in MoveMember, EncapsulateField, InlineClass.
- audit: 24 raw comparisons left after; use-vs-declaration ones in InlineFunction, Signatures,
  SubclassToStrategy swapped. `inline_function_static_of_generic_class` showed InlineFunction's
  ("has no calls in the project") before the swap. The rest compare two declarations, two locals
  or type variables, where the raw key is the declaration's.

## Measured

Not measured (no session-log hook here). About 16 tool calls: 12 refactorings, the rest edits.

## Assessment

Through the tools; the swaps of comparisons are `replace_text` patterns.

## Follow-ups

- `Signatures.key(IMethodBinding)` is the same rule for methods; its many callers were left.
- #500: `Bindings.erasedKey` in `ParameterValue`.
