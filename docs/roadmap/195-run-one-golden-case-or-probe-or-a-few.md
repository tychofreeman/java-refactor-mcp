---
order: 188
item: 195
status: Done
---

## Item

Run one golden case or probe, or a few: `GoldenTest` and `ProbeTest` ran every case, so sessions wrote scratch scripts to run one through the CLI and diff it

## Status

Done 2026-11-13 (`-Djrefactor.golden=<names>` and `-Djrefactor.probe=<names>`, comma-separated directory names or globs with `*` and `?`, read by `Cases.select` in both factories; a pattern that matches no case fails the run, naming it. Surefire's `-Dtest=GoldenTest#<case>` was tried first and selects nothing: its method filter sees the factory method `cases`, not the dynamic tests' names. Defaults to reopen (the owner said take the smallest): one property per test class over one shared, and failing on any pattern that matches nothing over failing only when none matches)

## Evidence

The owner's follow-up of `docs/sessions/move-record-fields.md`; `docs/sessions/golden-one-case.md`
