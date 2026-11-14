---
order: 91
item: 98
status: Done
---

## Item

Shared helpers instead of copies: `lineOf`/`lineStart` (copied in `ExtractVariable`, `InlineVariable`, `Primitives` though `Lines` has them), `indentOf` (three copies), `within` (two), and a query helper for the common "find nodes like this" walks (53 anonymous `ASTVisitor`s)

## Status

Done (2026-11-05), local session: `Nodes.end`, `Nodes.within`, `Lines.offsetOf`, `Lines.indentOf` replace twelve copies (80 calls folded by `inline_function`); `Nodes.all(root, type)` is the query, used by seven walks that only collected; 22 more single-type visitors convert when their classes next change; record in `docs/sessions/shared-helpers.md`

## Evidence

Same review. No behavior change; the goldens are the check. Needed two tool fixes first: #169 (`inline_function`, a subtype passed on to a call) and #170 (`NameCheck`, keys that hold positions)
