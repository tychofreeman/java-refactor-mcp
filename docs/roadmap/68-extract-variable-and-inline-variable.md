---
order: 61
item: 68
status: Done
---

## Item

`extract_variable` and `inline_variable` refuse what moves a read across an effect or a name across a declaration: extracting `i * 2` from `i++ + (i * 2)` (read moved before `i++`), a new name that hides a field the rest of the method reads, an inlined initializer whose field a closer local hides at the use

## Status

Done

## Evidence

Found by probes after `docs/sessions/stress-new-tools.md`; each applied and type-checked. `ExtractVariable`, `InlineVariable`
