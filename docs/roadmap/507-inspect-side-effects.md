---
order: 497
item: 507
status: Open
---

## Item

The `inspect` outline says, after each method's lines, complexity and nesting, whether calling it
has side effects, as gorefactor's (issue #369, last part): `no side effects`, or `side effects:`
and the first one, following calls into the project's own methods.

## Status

Built 2026-11-15 on branch `inspect-side-effects` (`SideEffects`, `Jdt.parseTexts`; goldens
`inspect_outline_side_effects`, `inspect_outline_side_effects_library`), not merged: on
commons-lang the outline of a file whose methods call other project files takes 2.6-3.8x as long
as without side effects (files that call only themselves and the JDK: no change), over the owner's
limit of about 2x. The owner chooses (the options are in the PR).

## Evidence

Issue #369, the parity review of 2026-11-13: gorefactor's outline gives side effects, and a model
can tell from it which methods it may reorder or call again without reading bodies. The owner chose
following calls everywhere (2026-11-15), over syntax only and over `inspect function` only.
