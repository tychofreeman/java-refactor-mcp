---
order: 72
item: 79
status: Done
---

## Item

`bin/jrefactor --rebuild` (or `JREFACTOR_REBUILD=1`) builds the jar from the working tree first, so a session can probe uncommitted server code

## Status

Done (2026-11-04), local session: `--rebuild` and `JREFACTOR_REBUILD=1` in `bin/jrefactor`

## Evidence

Friction log, 2026-11-04: probing #73's change took a temporary commit and a soft reset
