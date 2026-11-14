---
order: 88
item: 95
status: Done
---

## Item

`extract_struct` refuses a grouped parameter whose type uses a type variable or a type the new file cannot see (a private nested type), naming it, instead of the gate's error

## Status

Done (2026-11-04), local session: `ExtractStruct.unusable`; golden `extract_struct_refused_type_variable`; the Commons Math case (`SchurTransformer.ShiftInfo`) now refused by name

## Evidence

`RefactoringStressTest` on Commons Math: three `extract_struct` calls gated ("creating PairZq would add compile errors"). `ExtractStruct`
