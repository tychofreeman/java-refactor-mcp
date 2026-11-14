---
order: 383
item: 393
status: Done
---

## Item

The declaration key of a binding was written in seven copies, one (`ParameterValue`) erasing types, and uses were compared with declarations by raw `getKey()`: `move` of a generic static method (`<T> T id(T)`) found no calls and was refused by the gate, `encapsulate_field` of a field of `Box<T>` read through `Box<String>` too, and `inline` of a static method of a generic class said it had no calls (issue #496, code review)

## Status

Done 2026-11-13: `Bindings.declarationKey`, `Bindings.same` and `Bindings.erasedKey` (`ParameterValue`'s, kept and named: the owner's decision is #500); `NameCheck`, `SafeDelete`, `Rename`, `MoveToPackage`, `Fold`, `LoopToStream` and `ConvertLoop` call them; `MoveMember`, `EncapsulateField`, `InlineClass`, `InlineFunction`, `Signatures.sameField` and `SubclassToStrategy` compare uses by them. `Signatures.key` (methods) is left as it is, the same rule. Goldens `move_generic_static_method`, `encapsulate_field_generic`, `inline_function_static_of_generic_class`

## Evidence

Issue #496; `docs/sessions/binding-keys.md`
