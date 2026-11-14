---
order: 216
item: 224
status: Done
---

## Item

`convert_to_record`: an `equals(Point)` overload counted as defining `equals`, so the record's value-based `equals(Object)` came in without `value_semantics`; field annotations and `transient` were dropped; a private or package constructor became a public canonical one; a class with only its fields and constructor wrote code that did not parse (the constructor's and the last field's removals overlapped)

## Status

Done 2026-11-13: `ConvertToRecord.definesEquals` by the parameter's binding (`java.lang.Object`), the refusal naming the overload; `annotations` carries a field's annotations onto its component; a transient field and a constructor less visible than the class are refused (`access`); `remove` clips a removal to what an earlier one left. Defaults to reopen (the owner said take the smallest), in Decisions

## Evidence

Issue #476 (code review); probe `convert_to_record_equals_overload`
