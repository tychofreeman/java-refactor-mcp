---
order: 35
item: 42
status: Done
---

## Item

Server reports a non-refusal exception as "engine error" with no stack trace and nothing on stderr; `Tools.call` calls `Journal.abort()`, which may throw and hide the original error

## Status

Done

## Evidence

Full code review 2026-11-03. Fixed: the engine-error text has three frames and stderr has the trace; a failed undo is attached as suppressed (`Tools.abort`). Record: `docs/sessions/server-engine-error.md`
