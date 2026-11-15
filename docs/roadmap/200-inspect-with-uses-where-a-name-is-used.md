---
order: 193
item: 200
status: Partly done
---

## Item

`inspect` with `uses`: where a name is used, found by bindings rather than by text; then `function`, `targets` and a per-body summary (complexity, nesting, side effects), gorefactor's names and format, one PR each

## Status

Partly done 2026-11-15: `uses` (#504), `function` and `targets` (#505), each body's lines, complexity and nesting (#506); each body's side effects wait for the owner's choice (issue #369). Those rows were added beside this one by mistake; this row is the item

## Evidence

Issue #369 (parity review, 2026-11-13): no find-uses here, and in gorefactor's A/B runs about a quarter of input went to "where is X used" searches
