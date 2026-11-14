---
order: 101
item: 108
status: Done
---

## Item

A batch checked at the end fixes imports after its last step, so a name a later step declares (a member type) is not imported from a library

## Status

Done 2026-11-06: the later step's import fix removes an import the batch added that a type of the file shadows

## Evidence

Friction triage: `javax.management.Query` imported for a nested record a later step added (2026-11-03), reproduced on 2026-11-05
