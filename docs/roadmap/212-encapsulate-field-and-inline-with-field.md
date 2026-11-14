---
order: 205
item: 212
status: Done
---

## Item

`encapsulate_field` and `inline` with `field` added an instance method that overrode one the class inherited (a getter `getName()` over `Base.getName()`), or that a subclass's method then overrode: it type-checked and calls ran another body (issue #466, code review)

## Status

Done 2026-11-13 (`Overrides.of`, `FunctionToMethod`'s check made shared: the first instance method of the name and erased parameters in a supertype, transitively, or in a project type below; `EncapsulateField.refuseOverride` for the getter and setter, `InlineClass.refuseOverride` for each folded instance method; probes `encapsulate_field_inherited_getter`, `_subclass_getter`, `inline_class_inherited_method`, `_subclass_method`. Default to reopen: a static field's accessors and a folded static method are not checked, as `FunctionToMethod` never checked statics; a static accessor can still hide a supertype's static method)

## Evidence

Issue #466; `docs/sessions/fix-new-override.md`
