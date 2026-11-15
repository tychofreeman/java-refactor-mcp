---
order: 494
item: 504
status: Done
---

## Item

`inspect` with `uses`: every use of a type, field or method in the project, found by binding,
under what the use does with it, as gorefactor's (issue #369, first of its four parts).

## Status

Done 2026-11-15: `Uses.of`, routed from `Tools.inspect`. Golden `inspect_uses` (a field, a
method, a class, a missing name) and `inspect_uses_refused_with_file`. The roles Java adds or
renames are a default in `docs/design.md`. `function`, `targets` and the body summary follow.

## Evidence

The coverage review of 2026-11-13 found no find-uses here; in gorefactor's A/B runs about a
quarter of input went to "where is X used" searches (issue #369). Sessions here used `inspect`
`pattern`, a text search, for the same question.
