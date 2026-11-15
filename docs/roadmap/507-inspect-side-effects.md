---
order: 497
item: 507
status: Done
---

## Item

The `inspect` outline says, after each method's lines, complexity and nesting, whether calling it
has side effects, as gorefactor's (issue #369, last part): `no side effects`, or `side effects:`
and the first one, following calls into the project's own methods.

## Status

Done 2026-11-15: `SideEffects`, `Jdt.parseTexts`, and the session's cache of each called file's
events (`Session.sideEffects`, the owner's option (B)); goldens `inspect_outline_side_effects`,
`inspect_outline_side_effects_library`, and `SideEffectsTest` for the cache. On commons-lang a
first outline of a file whose methods call other project files takes 2.6-4.8x as long as without
side effects; a later one, about the same (1.0-1.2x). Numbers in `docs/design.md`.

## Evidence

Issue #369, the parity review of 2026-11-13: gorefactor's outline gives side effects, and a model
can tell from it which methods it may reorder or call again without reading bodies. The owner chose
following calls everywhere (2026-11-15), over syntax only and over `inspect function` only.
