---
order: 390
item: 400
status: Done
---

## Item

"Resolve a type as a class's members see it" (a probe field, `Imports.fix`, a parse) was written three times, each inserting the field at its own place (issue #493, code review): `ThrowsCallers.exceptionSupers` after the first `{` after the name (before an enum's constants), `SubclassToStrategy.samOf` at the end of the name's line (inside a wrapped header), `TypeParameters.value` after the header

## Status

Done 2026-11-14: one `Jdt.probeType` (the binding, or null) over `Jdt.withProbe`, which declares the field before the type's first member, else at the end of its body after a `;` for an enum; the three callers use it, and `TypeParameters.headerEnd` is gone. The breakages the review suspected did not reproduce through the tools (`change_signature` `add_throws` on enum methods with and without constant bodies, `subclass_to_strategy` with a wrapped header): JDT's recovery resolved the misplaced field. `JdtTest` pins that the probed text parses with no problem and holds the field as a member for seven shapes (enums with and without members, a wrapped header, a one-line body, a record, an interface), and that a name resolves in an enum's scope

## Evidence

Issue #493; `docs/sessions/probe-type.md`
