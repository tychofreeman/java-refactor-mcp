---
order: 26
item: 33
status: Done
---

## Item

Static mutable state: `Journal.DONE`, `Imports.LIBRARIES`, `Server.CALLS`, `Stubs.DECLARED`; tests in one JVM can interfere

## Status

Done

## Evidence

Code review 2026-11-03. Safe in the server today: one call at a time
