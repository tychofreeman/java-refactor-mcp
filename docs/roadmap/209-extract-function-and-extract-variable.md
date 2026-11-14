---
order: 201
item: 209
status: Done
---

## Item

`extract_function` and `extract_variable` checked a new name only against the type and its supertypes: a method of an enclosing type, or a field of an enclosing type, an enum, a record or an interface, was captured silently (issue #469, code review)

## Status

Done 2026-11-13 (`Extraction.refuseEnclosing`, from `refuseClash` and `checkOverride`; `ExtractVariable.checkFieldName` walks every `AbstractTypeDeclaration` and anonymous class around the expression, superclasses and interfaces; probes `extract_*_hides_*`, goldens `extract_function_refused_outer_method`, `extract_variable_refused_hides_enum_field`. Default to reopen in Decisions: the walk over a meaning check)

## Evidence

Issue #469; `docs/sessions/fix-extract-name-clash.md`
