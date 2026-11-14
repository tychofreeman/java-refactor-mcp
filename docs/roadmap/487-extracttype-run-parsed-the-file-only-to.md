---
order: 477
item: 487
status: Done
---

## Item

`ExtractType.run` parsed the file only to route, and `fields` parsed it again; `fields` found a record by its own loop, as `Opened.classOrRecord` does; "create a new type's file through the import fix and the gate" was written three times in `ExtractType` beside `ExtractSupertype.create`, and the package header five times (issue #518, code review)

## Status

Done 2026-11-14: `run` passes its `Opened` to `parameters` and `fields`, which asks `classOrRecord`; `Edits.newFile(project, target, text, type, chosen, fix)` (was `ExtractSupertype.create`, moved; `fold` then reached ExtractType's three copies once their `Imports.fix(...)` was named), each caller keeping its refusal's last sentence; `Opened.packageHeader()` and `Opened.packageHeader(pkg)` for `ExtractType`, `ExtractSupertype`, `Skeleton`, `Stubs` and `MoveType`. No behavior change: the goldens are the check. `MoveToPackage` inserts a header into an existing file, unchanged; the package-name lookups are #511's

## Evidence

Issue #518; `docs/sessions/extract-type-sibling.md`
