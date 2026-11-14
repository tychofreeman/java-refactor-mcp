---
order: 36
item: 43
status: Done
---

## Item

Names: `Stubs.label` duplicates `Rename.typeLabel`; `Stubs.typeName` and `Extract.typeName` disagree on type variables and captures; `Signatures.label` drops the outer class that `typeLabel` keeps

## Status

Done (2026-11-04) Closed 2026-11-04: `Stubs.label` and `Rename.typeLabel` are gone, `Signatures.label` uses `Names.typeLabel`, and wildcards were decided (`docs/sessions/names-close-43.md`).

## Evidence

Full code review 2026-11-03. `Names.typeLabel` replaces `Rename.typeLabel` and `Stubs.label`; `Signatures.label` keeps the outer class (golden `sig_add_refused_nested_class`). Type variables decided 2026-11-04: a stub writes a type variable by name when the type it goes in declares it, and erases it otherwise (`docs/sessions/stubs-type-variables.md`). Open: wildcards, which `Stubs` erases and `Extract` keeps. Record: `docs/sessions/names-one-class.md` Wildcards (decided 2026-11-04): a stub keeps `? extends T` as `Extract` does; captures are erased in both (`docs/sessions/stubs-keep-wildcards.md`). Golden: `stub_wildcard_argument`.
