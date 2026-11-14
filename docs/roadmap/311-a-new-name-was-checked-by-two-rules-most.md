---
order: 301
item: 311
status: Done
---

## Item

A new name was checked by two rules: most tools took one identifier, but `extract_function`, `add_parameter` (and its record and enum forms) took `SourceVersion.isName`, which accepts `a.b`; the edit then failed to parse or compile, refused as the tool's fault or with compile errors, not as a bad name (issue #494, code review)

## Status

Done 2026-11-13 (`Precondition.identifier(name, what)` at every site that takes one name: `AddResult`, `ExtractSupertype`, `ExtractVariable`, `Extract`, `Rename`, `Signatures.checkNewParameter`, `ExtractType` (4), `TypeSwitch`; one wording for all, `Rename`'s hint kept; goldens `extract_function_refused_dotted_name`, `add_parameter_refused_dotted_name`)

## Evidence

Issue #494; `docs/sessions/fix-identifier-names.md`
