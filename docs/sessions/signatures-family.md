# The family walk to MethodFamily (#528, item 2, part 1)

- **Item:** issue #528, item 2 of its last comment: `Signatures.java` (about 1250 lines); the
  family records and `family`, `key`, `label`, `constructed` to `MethodFamily`, and the refusal
  text of `removeWithExpression` to `ParameterValue`. Started once #375 (TypeParameters) closed
  and no PR touched the signature tools.
- **Date:** 2026-11-15
- **Branch:** signatures-family

## Plan

`move` of the static helpers `family`, `key` and `constructed` to `MethodFamily` in one batch.
The records next, if a tool moves a member type into another class.

## Notes

- refusal: `move` of `Signatures.family`: "contains an anonymous class or a local class, whose
  this is not the receiver" (for a static method, which has no receiver). Its anonymous
  `ASTVisitor` became a loop over `Nodes.all(unit, MethodDeclaration.class)` (the session notes'
  rule, #98) by `replace_statements`, in the same batch as the moves; the order the methods are
  visited is the same (preorder).
- `move` of `family`, `key` (16 calls in 4 files) and `constructed` (2 calls): Signatures 1250 ->
  about 1150 lines.
- refusal: `move` of `Signatures.Decl` to `MethodFamily`: "Signatures declares no method or field
  Decl". A member type moves only to its own file. The five records are named in about 100 lines
  of seven files; by hand that is a large write, so they stay (friction log).
- `label` is already gone from Signatures (`Names.methodLabel`, #501).
- Not done: `removeWithExpression`'s refusal text to `ParameterValue`.

## Measured

Not measured (no session-log hook). 1 batch (`replace_statements`, 3 `move`), 1 refused `move`;
no Java by hand.

## Assessment

Through the tools, but only the methods: the records, which are most of what the review meant
by "the family", need a tool that does not exist.

## Follow-ups

- `move` of a member type into another class (`Signatures.Decl` to `MethodFamily.Decl`).
- `move` of a static method that holds an anonymous class.
- `removeWithExpression`'s refusals to `ParameterValue`.
