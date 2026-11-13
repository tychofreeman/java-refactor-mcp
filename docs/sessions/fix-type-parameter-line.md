# change_type with type_parameter takes line

- **Item:** roadmap #176 (order 169), issue #407, from the Commons Lang stress run
  (`docs/sessions/stress-new-options.md`); the owner asked for the fix on 2026-11-13.
- **Date:** 2026-11-13
- **Branch:** fix-type-parameter-line (created as fix-method-naming)

## Plan

`Tools.changeType` routes `type_parameter` to `TypeParameters.bound` before `ByLine.named` turns
`line` into `name`, so `bound`'s precondition refuses. The one edit: pass the args through
`ByLine.named` when they have `line`, keeping `bound`'s own refusal (which names a class or a
method) when neither is given. Golden first: the issue's reproduction.

## Notes

- read: by `sed`, not `inspect`, of `Tools.changeType`,
  `TypeParameters.bound` and `ByLine` (the brief's agent shell, no MCP server; the read guard is
  not loaded).
- plan: golden `change_type_bound_by_line` (the issue's `Lib.apply`, `line=6`): red with the
  issue's refusal.
- edit: `bin/jrefactor replace_text` on `Tools.changeType`, one line. Green.
- hand: docs (contract, roadmap, session notes, this record): non-Java files, by script.

## Measured

Not measured: the session-log hook is not loaded in this session. One tool call to Java (one
`replace_text`), no refusals.

## Assessment

Through the tools for the one Java change; the plan held. A by-line route that `ByLine.named`
skips when it is not called is easy to miss for a new form of a tool: the other tools call it in
their `Def` lambda, this one branched before it.

## Follow-ups

- #408 (the next item): `ByLine.named` writes the method's written label, so `line` of one of two
  overloads written alike (`raise(T)`) still names it ambiguously.
