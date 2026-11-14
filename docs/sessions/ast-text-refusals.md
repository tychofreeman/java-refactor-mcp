# Refusals quote source text, not toString (issue #526)

- **Item:** code-review issue #526 (group 4 of #532): refusals and reports print AST nodes by
  `toString()`, and the may-throw phrase is rebuilt about ten times.
- **Date:** 2026-11-14
- **Branch:** ast-text-refusals

## Plan

One `show(node)`: the node's source text on one line, cut short. The units need their text: the
parse keeps it as a property (`Jdt.parse`, `parseSyntax`), and a unit from `parseAll` reads its
file. Then each `+ e.node()` and `+ throwing.get(0)` in `Behavior` goes through it, the latter
through one `mayThrow(who, throwing)`; `InlineFunction`'s receiver too. Red first: a golden where
`inline_variable` crosses a call with a lambda argument.

## Notes

- read: `Behavior` 1-512 through `inspect` (line ranges), `Jdt`'s parse entry points, `Fold`
  at the reviewed commit: its line 93 is a method label, not a node, so nothing to change there.
- plan: `show` in `Nodes` rather than `Behavior` ("where syntax nodes are in their source text"),
  since `InlineFunction` uses it too.
- red: golden `inline_variable_refused_lambda_between` printed "names.forEach(n -> {" and the rest
  on further lines.
- bulk: one `batch` of 12 steps (add_declaration, replace_text) with check "end".
- refusal-ish: `replace_text` with `all_occurrences` and a multi-line `old` replaced 4 of 7 sites:
  the others sit at other indentations, and a match keeps one shift for the whole block. The
  result named only the 4, so I grepped for the rest; a second `batch` of 4 steps did them.
  Logged in the friction log.
- create_file of `NodesTest`; spotless after.
- hand: docs only.

## Measured

Not measured (no session-log hook here). From the notes: 4 tool calls wrote all the Java (two
batches, 16 steps; `create_file`); nothing by hand in `src/`.

## Assessment

Through the tools. The plan held. One more call than needed, for the indentation of
`all_occurrences`.

## Follow-ups

- Other tools may quote nodes by `toString()` too (the review named only these); `grep` for
  `+ node` in refusals when one is next touched.
