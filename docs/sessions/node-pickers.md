# One line-and-column node picker (issue #520)

- **Item:** code-review issue #520 (group 4 of #532): line/column node pickers re-implemented in
  ConvertLambda, ExtractVariable and InlineVariable, with four wordings of one refusal (one garbled).
- **Date:** 2026-11-14
- **Branch:** node-pickers

## Plan

One picker on `Opened`: the nodes that start on a line (by `unit.getLineNumber`, through
`Nodes.all`), narrowed by column with one refusal that lists `Nodes.columns`, and one refusal for
several when the model must pass column. `startingOn` gets a `column` overload built on them.
Operations: add the helpers (`add_declaration`), point `ConvertLambda`'s visitor at the overload
(`replace_text`), rewrite `ExtractVariable.find` and `InlineVariable.variableAt` onto the helpers
(`replace_declaration`), and `ConvertToRecord.refuseSubclasses`' visitor to `Nodes.all`. Red first:
goldens for the garbled `inline_variable` refusal and `convert_lambda` with a wrong column (which
names no columns today).

## Notes

- read: OpenedTest 15-56 to match its test style
- read: the five sites through `inspect` (`names` for the pickers, line ranges for ConvertLambda,
  ConvertToRecord, ByLine), and OpenedTest's node test.
- plan: the goldens first; all four failed with the old wordings (the garbled one listed columns
  13, 23 and 27 for two variables).
- bulk: one `batch` of 9 steps (replace_text, add_declaration, replace_declaration) with check
  "end": the helpers on `Opened`, the three pickers rewritten onto them, the callers, and
  `ConvertToRecord.refuseSubclasses`; imports removed by the tool. Passed first time.
- plan: `ConvertLambda.refuseSelf` keeps its visitor: it prunes anonymous classes, which
  `Nodes.all` cannot, so a `this` inside a nested anonymous class would be refused. `ByLine`'s
  refusals name methods, not nodes, and it already reads `unit.getLineNumber`: left.
- add_declaration of the unit test; spotless after.
- hand: docs (contract, design row, session notes, this record) with a script; not Java.

## Measured

Not measured (no session-log hook here). From the notes: 2 tool calls wrote all the Java (one
batch of 9 steps, one add_declaration); nothing by hand in `src/`.

## Assessment

Through the tools. The plan held: the picker's pieces went on `Opened` as planned, with one
change, `onLine` and `atColumn` public to the package, since `ExtractVariable` and `InlineVariable`
match by text and by binding first. Reading was by `inspect` only.

## Follow-ups

- `ByLine` could take its line start from `Opened`, but its refusals are about methods.
