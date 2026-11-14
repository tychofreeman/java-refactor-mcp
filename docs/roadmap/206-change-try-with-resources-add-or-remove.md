---
order: 199
item: 206
status: Done
---

## Item

`change_try` with resources: add or remove a resource of a try-with-resources

## Status

Done 2026-11-14 (`ChangeTry.addResource`, `removeResource`; the user chose "move it out and close it" and "text with a position", each over two options, in Decisions; 11 goldens `change_try_*resource*`, 5 probes `change_try_remove_resource_*`; default to reopen in Decisions: the close in a new inner try's finally, not the existing finally)

## Evidence

Issue #364's later step; row 199 built the rest; `docs/sessions/change-try-resources.md`
