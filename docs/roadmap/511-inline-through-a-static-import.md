---
order: 501
item: 511
status: Done
---

## Item

`inline` of a method a caller reaches through a single static import (`import static p.Keys.erased;`): the import goes with the deleted method (issue #644)

## Status

Done (2026-11-15): `Imports.plan` drops a single static import whose simple name the file used before the edit and no longer uses, as it drops a type import; an on-demand `import static p.Keys.*;` stays. Goldens `inline_function_qualified_own_member_static_import` (applied; it pinned the refusal) and `..._on_demand`

## Evidence

Issue #644, after #624 (issue #621): the call was inlined and the method deleted, and the type check refused the import that still named it
