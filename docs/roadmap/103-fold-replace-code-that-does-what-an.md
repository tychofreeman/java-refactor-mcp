---
order: 96
item: 103
status: Done
---

## Item

Fold: replace code that does what an existing method does with a call of it (gorefactor's `fold`), so near-duplicates merge by extract, then fold

## Status

Done (2026-11-05), local session: `fold` (`Fold`), static methods, expression and statement bodies, the `String.format` text relaxation; the warehouse dedupe is `add_declaration` of the helper and one `fold` (9 of 10 folded, `Gas`'s `>` left; the A/B's hidden test passes); record in `docs/sessions/fold.md`

## Evidence

A/B rerun: `warehouse-dedupe` cost the mcp arm $0.15 against $0.09, with no refusals: reading (14 `inspect`s in 4 runs) and composing each of ten replacements by hand. gorefactor's derivation for this task is extract_variable, extract_function, fold
