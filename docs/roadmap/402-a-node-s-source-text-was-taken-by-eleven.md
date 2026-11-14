---
order: 392
item: 402
status: Done
---

## Item

A node's source text was taken by eleven private helpers (`source` in `ConvertLambda`, `ConvertToRecord`, `EncapsulateField`, `InvertIf`, `ConvertLoop`, `LoopToStream`, `Generated`, `MoveMember`, `RemoveElse`; `src` in `SubclassToStrategy` and `ChangeSignature`; `MemberMove.textOf`) and about 45 inline `text.substring(n.getStartPosition(), ...)`, and its end offset by about 45 inline `getStartPosition() + getLength()` beside `Nodes.end` (issue #507, code review)

## Status

Done 2026-11-14: `Nodes.source(text, node)` (moved from `Generated`; named `source`, since `text` is its parameter's name and `rename` refused the capture); `fold` of `Nodes.end` (44 sites) and then of `Nodes.source` (46 sites) rewrote the copies, and `inline` removed the eleven helpers. No behavior change: the goldens and probes are the check, `NodesTest.aNodesSourceIsItsTextAsWritten` pins the helper. Left: ten sites `fold` left because a call would evaluate a getter once (`d.getJavadoc()` twice, ...); `Signatures.Source.text(n)` and the review's other helpers (`Lines.skipSpace`, `Edits.dropModifier`)

## Evidence

Issue #507; `docs/sessions/node-text.md`
