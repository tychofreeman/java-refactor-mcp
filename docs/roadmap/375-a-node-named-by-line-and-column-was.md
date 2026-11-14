---
order: 365
item: 375
status: Done
---

## Item

A node named by line and column was found by four pickers (`Opened.startingOn` without a column, `ConvertLambda`, `ExtractVariable.find`, `InlineVariable.variableAt`), each with its own visitor or line scan and its own refusal: `inline_variable` said "pass column, the one of the one you mean" and listed every use's column, `convert_lambda` with a column where none starts named no columns (issue #520, code review)

## Status

Done 2026-11-14: `Opened.onLine` (by `unit.getLineNumber`, through `Nodes.all`), `Opened.atColumn` and `Opened.several` give one wording, and `startingOn` takes a `column`; the three tools call them, and `ConvertToRecord.refuseSubclasses` walks `Nodes.all`. `ConvertLambda.refuseSelf` keeps its visitor (it prunes anonymous classes), and `ByLine` its method-naming refusals. Goldens `inline_variable_refused_two_variables`, `convert_lambda_refused_wrong_column`, `convert_lambda_refused_two_on_line` (each failed before); `OpenedTest.picksTheNodeOnALineByColumn`

## Evidence

Issue #520; `docs/sessions/node-pickers.md`
