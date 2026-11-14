---
order: 472
item: 482
status: Done
---

## Item

Line and offset arithmetic was written again beside `Lines` (issue #512, code review): `Positions.offset` (one caller), `Imports.count`, `Inspect.end`, `Extraction.indentOf` and `lineStartOffset`, `Extract.lineEndOffset`, `InlineVariable.removeLine`'s own line bounds

## Status

Done 2026-11-14: `Lines.offsetOf(text, line, column)` with `Positions`' refusals (`LinesTest`), `Positions` deleted; the others call `Lines.number`, `start`, `end`, `indentOf` and `Nodes.end`. No behavior change; every golden and probe passes. The issue's other parts were done before this PR: `Opened.startingOn` asks `unit.getLineNumber` (by #520's `onLine`), and `InlineFunction` removes a method with `MemberMove.removal` (#504). Left: `Declarations`, `RemoveElse` and `IndentShift`, which the review put outside its scope

## Evidence

Issue #512; `docs/sessions/lines-helpers.md`
