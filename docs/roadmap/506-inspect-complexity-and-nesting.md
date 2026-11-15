---
order: 496
item: 506
status: Done
---

## Item

The `inspect` outline gives each body's lines, cyclomatic complexity and nesting depth, as
gorefactor's (issue #369, third part). Its side effects wait for the owner's choice.

## Status

Done 2026-11-15: `Inspect.facts` and `Inspect.metrics`, written by `Inspect.outline` for each
elided body and after each body too short to elide. Goldens `inspect_outline` and
`inspect_names_nested_type` changed; `inspect_outline_metrics` pins complexity 11 and nesting 3.
The metric's Java branch points are defaults in `docs/design.md`. Side effects: not built, the
options are on issue #369.

## Evidence

Issue #369, the parity review of 2026-11-13: gorefactor's outline gives them, and a model picks
the method to split from them without reading bodies.
