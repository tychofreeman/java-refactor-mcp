---
order: 522
item: 532
status: Done
---

## Item

`extract_variable` drops the parentheses around the expression it replaces with a name: `(a + b) * 2` gives `sum * 2`, not `(sum) * 2`.

## Status

Done (2026-11-20, issue #698): `Nodes.parenthesized`, in `ExtractVariable` and both places in `Introduce` (as a field or constant, and as a parameter).

## Evidence

gorefactor's TypeScript issue #717, checked against Java on 2026-11-20: the `extract_variable` half reproduces here; `function_to_method` writes `new Reader(text).head()` already.
