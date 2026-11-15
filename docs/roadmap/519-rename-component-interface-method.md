---
order: 509
item: 519
status: Done
---

## Item

`rename` of a record component whose accessor implements an interface method renames that method too, with every other implementation and every call through the interface (issue #674; the owner's option (a), over refusing)

## Status

Done (2026-11-15): `Rename.accessorFamily` gives the keys of `MethodFamily.family` from each method the accessor overrides; `renameComponent` renames each name bound to one of them, refuses a clash in any declaring type (`refuseMethodClash`) and an accessor that implements a library method, and names the other methods in its result. Goldens `rename_record_component_interface` (a class implementer, a call and a method reference through the interface, a call through the record), `_interface_written_accessor`, `_interface_refused_clash`, `rename_record_component_refused_library`

## Evidence

Issue #674, the follow-up of #671 (issue #659): the interface method kept its name, so the record no longer implemented it and the gate refused
