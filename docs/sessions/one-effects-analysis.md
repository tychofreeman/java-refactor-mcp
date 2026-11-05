# One effects analysis (roadmap #72)

- **Item:** roadmap #72: one model of what code does, shared by the tools
  that move code, in place of `ExtractVariable.hasEffect` (and
  `checkEarlierEffects`), `MoveStatement.Effects`,
  `InlineVariable.assigns`/`readsIn` and `InlineFunction`'s `fixed` and
  `effectsIn`. The user asked for #71-#73 in succession (2026-11-04) and was
  away; decisions are this session's, listed below.
- **Date:** 2026-11-04
- **Branch:** one-effects-analysis

## Decisions (made by this session; the user was away)

- **Shape.** `Effects.of(node)`: a list of `Effect(kind, node, variable,
  deferred)` in source order (a preorder walk, which is Java's evaluation
  order for the shapes these tools move), with kinds `NAME` (a variable
  named), `ELEMENT` (an array element or a field through an expression),
  `DECLARE`, `WRITE`, `CALL`, `CREATE`, `NEW_ARRAY`. `deferred` marks what
  runs in a lambda or an anonymous class. Queries: `changes()` (any write,
  call or creation), `named()`, `written()`, `writes(variable, from)`,
  `normalFlowChanges()`, `fixed()`. Not chosen: a set per kind (the order
  is what #73 needs: "a dereference moved past a guard").
- **Room for exceptions.** A further kind (a dereference, a call that
  declares a checked exception) goes in the same list for #73; nothing is
  added for it here.
- **Behavior.** Each old model is matched by a query, kind for kind, and the
  goldens are the check. One difference, taken on purpose:
  `InlineVariable.assigns` matched only a bare name as the assigned target,
  so `this.n = 5` after `int a = n;` was not seen; `writes` counts a field
  access too, so that case is now refused. A golden pins it.

## Plan

1. `create_file` `Effects` and `EffectsTest` (one batch, as in #71: a test
   written first would get its stub in the test tree).
2. Each old model's body becomes a call of `Effects` (`replace_text` or
   `replace_declaration`), goldens after each.
3. Then remove the old helpers with the tools: `inline_function` for those
   whose body is now one `return` (`ExtractVariable.hasEffect`,
   `InlineFunction.fixed`, `InlineVariable.assigns`), `replace_declaration`
   with empty content for `MoveStatement.Effects`.
4. Golden `inline_variable_refused_field_written` for the difference above.

## Notes

- `read:` the four models (`MoveStatement.Effects`, `ExtractVariable`'s
  `hasEffect` and `checkEarlierEffects`, `InlineVariable.assigns`/`readsIn`;
  `InlineFunction`'s were this session's own), by `sed`, to match each kind
  for kind.
- `plan:` `written()` and `writes()` compare variables as declared
  (`getVariableDeclaration`), so a field seen through a generic type still
  matches; `MoveStatement` compared raw bindings. A widening, like the field
  access one, not a narrowing.
- Step 3 went as planned: one batch of five `inline_function` steps removed
  the delegates (7, 3, 1, 2 and 2 calls), and `replace_declaration` with
  empty content removed `InlineFunction.changes`, left with no caller. The
  tool built this session inlined its own helpers.
- The new golden is red on `main` (checked in a worktree of `main` with the
  case copied in: "Expected Refusal to be thrown") and green here.
- Stress run on Commons Lang (seed 3, 110 calls) on the branch: no
  failures; the tools that use `Effects` applied (`extract_variable` 6,
  `inline_variable` 6, `inline_function` 1).

## Measured

From `bin/session-report` (before the docs edits):

- tool calls: 18
- through jrefactor: 5 refactoring steps (`inline_function`), 19 edit steps
- refused by jrefactor: 0
- by hand: 2 (the record; a shell command), then the docs by `sed` and a
  heredoc
- large writes: 2 (`Effects` and `EffectsTest`, new)
- reads: Bash java 2

## Assessment

- **Through the tools or around them:** through. The old models were
  replaced by `replace_declaration` (their bodies became one call) and then
  removed by `inline_function`, not deleted by hand.
- **Large writes:** `Effects` is new code that subsumes four old visitors;
  it was written, not moved, because none of the four had the shape (an
  ordered list) the others could have grown into.
- **Did the plan hold?** Yes.
- **One change that would have helped:** none.

## Follow-ups

- `ExtractVariable.checkPlace` (loop conditions, `&&`/`||`, conditionals) and
  `InlineFunction.deferral` still walk parents for "runs only sometimes";
  that could be a mark on `Effect` (`conditional`) when a third tool needs it.
- Kinds for what may throw: #73.
