---
order: 43
item: 50
status: Done
---

## Item

Process-global state cannot be isolated by tests: `Server` (`ONE_AT_A_TIME`, `CALLS`, `lastOutput`), `Imports.LIBRARIES` (never cleared), `Journal.DONE` (undo across projects)

## Status

Done

## Evidence

Full code review 2026-11-03: reading, not run. Overlaps #33 (static state); a `Session` object would hold these. Decide with #33
