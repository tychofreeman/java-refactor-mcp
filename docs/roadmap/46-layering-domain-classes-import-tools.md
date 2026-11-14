---
order: 39
item: 46
status: Done
---

## Item

Layering: domain classes import `Tools.lines` and call `Edits.lineOf`, so they depend on the MCP registry; line helpers are spread over six classes

## Status

Done (2026-11-04): the line helpers were gone after #27; the result type is `Outcome` (its own file). `Edits.gated`, `parses` and `Written` stay in `Edits`, by choice (`docs/sessions/layering-line-helpers.md`)

## Evidence

Full code review 2026-11-03: reading, not run. Overlaps #27 (the line helper). Move the helpers to their own class first
