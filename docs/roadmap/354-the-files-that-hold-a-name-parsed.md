---
order: 344
item: 354
status: Done
---

## Item

"The files that hold a name, parsed together" was written out by hand in `MethodFamily`, `MoveMember` (twice), `EncapsulateField`, `InlineFunction`, `ExtractType`, `ParameterValue` and `ThrowsCallers`, because `Holding.of` took one extra file; the copies differed in path normalization and in whose text they kept; `inspect` `names` parsed with bindings it never used (issue #502, code review)

## Status

Done 2026-11-14: `Holding.of(project, name, Path... also)` (each `also` normalized) and `Holding.where(project, (file, text) -> ...)` for any other test; the eight copies call them. `inspect` `names` reads each file holding the type's simple name and parses it for syntax only (`Jdt.parseSyntax`). No behavior change: the goldens are the check, `HoldingTest` pins the helper. Default to reopen: no map of the caller's texts (the issue's suggestion), since the units are parsed from disk and a text other than the file's could not match them

## Evidence

Issue #502; `docs/sessions/holding-names.md`
