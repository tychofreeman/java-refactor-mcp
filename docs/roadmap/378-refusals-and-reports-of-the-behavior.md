---
order: 368
item: 378
status: Done
---

## Item

Refusals and reports of the behavior gate quoted code by JDT's `toString()`, a reformatting: a call with a lambda argument or an anonymous class came out over several lines inside a one-line refusal, in a form the model cannot find in the file; and the may-throw phrase was built ten times (issue #526, code review)

## Status

Done 2026-11-14: `Nodes.show(node)` quotes the node's source text (`Jdt.sourceOf`: the text given to `Jdt.parse` or `parseSyntax`, kept as a unit property, else the file on disk), on one line, cut to 60 characters with "..."; `Behavior` and `InlineFunction`'s receivers call it, and `Behavior.mayThrow` writes "<who> may throw (<code>)". The review's `Fold.java:93` quotes a label, not a node: unchanged. Golden `inline_variable_refused_lambda_between` (failed before), `NodesTest`

## Evidence

Issue #526; `docs/sessions/ast-text-refusals.md`
