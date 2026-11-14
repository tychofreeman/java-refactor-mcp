# Ancestor and type-around lookups in Nodes

- **Item:** issue #508 (code review, group 4 of #532): `Nodes` lacks ancestor and type-around
  lookups, so classes write their own.
- **Date:** 2026-11-14
- **Branch:** nodes-ancestor

## Plan

One set of node lookups in `Nodes`. Operations: `move` the existing helpers there
(`Extract.ancestorAbove`, `Stubs.ancestorOrSelf`, `Introduce.enclosing`, `Fold.typeAround`,
`Guards.strip`), `rename` the moved ones to the shared names, then for each other copy a delegate
and `inline` (or `fold`), and swap the inline parent loops with `replace_statements`. No behavior
change expected; the goldens are the check, `NodesTest` pins the helpers. Skip files another open
PR rewrites (`Rename`, `TypeSwitch`, `Primitives`).

## Notes

- read: `inspect` `names` for the eight helpers at once, `pattern` for parenthesis strippers.
- refusal: `fold Nodes.ancestor`: fold takes one return or a void body.
- refusal: `inline` of the generic `SubclassToStrategy.ancestor` delegate; `replace_text` of the
  calls and a delete instead.
- refusal: `inline` of `FieldOrder.ancestor` (argument `SimpleName` against `ASTNode`).
- refusal: `rename` without `line`.
- plan: `ExtractVariable.enclosingStatement` had no null check: probed `extract_variable` in a
  field initializer, which threw a `NullPointerException` (local and `as=constant` alike). The swap
  would have turned it into another NPE; fixed instead: refused, but for `as=constant`, which
  `Introduce` already handles. `as=field` was refused by the gate with a misleading hint ("Choose a
  name"), so it gets the same refusal. Goldens first, failing.
- read: the review's one-level strippers (`ChangeSignature.quietAndSafe`, `NonNull.mayBeNull`)
  recurse; nothing to fix.
- hand: the rebase conflict with #526's `Nodes.show` and its `NodesTest`.

## Measured

Not measured (the session-log hook is not loaded); calls are in `JREFACTOR_LOG`. From the notes:
Java changes in nine tool calls (four batches, `create_file`, `replace_declaration`,
`replace_expression`, `replace_statements`), none by hand but the rebase merge.

## Assessment

Through the tools. The plan held for the moves and renames; the delegate-then-inline step failed
twice (generic, subtype argument), each worked around with `replace_text` of the calls. One change
that would have helped: `inline` of a generic method whose body is one call (logged).

## Follow-ups

- `Rename`, `TypeSwitch` and `Primitives` parent loops, left while other work changes those files.
- `MoveMember.owningType` and `Build.owner` (two kinds each), `ParameterValue.unwrapped`.
