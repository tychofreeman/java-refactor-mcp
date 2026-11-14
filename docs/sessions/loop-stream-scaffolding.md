# LoopToStream: one set of scaffolding for its rewrites

- **Item:** issue #516 (code review, group 4 of #532).
- **Date:** 2026-11-14
- **Branch:** loop-stream-scaffolding

## Plan

Name each repeated piece and route the copies through it, the loop goldens and probes as the
check: the local just before the loop, the empty-collection test, the text before an initializer,
the stream head with its stages, the use count, and the layout. Where copies differ, pin the
difference with a golden first.

## Notes

- plan: `add_declaration` of `uses` and `beforeInitializer`, `fold` of each: `uses` folded 3 of 4,
  `beforeInitializer` 1 of 5. `fold` left the rest because a getter argument (`out.resolveBinding()`,
  `v.getInitializer()`) "calls" and would move past another call: it cannot know a getter is pure.
- refusal: `remove_parameter` `init` with `value` `fragment(d).getInitializer()`: one call passed a
  local holding it. Rewrote `beforeInitializer` to take the declaration and replaced the five
  `String prefix = ...` statements with `replace_statements`, in one batch.
- hand (by tool, wrong lines): a `replace_statements` range one line early replaced a declaration
  and the gate declared a field stub for its name; `undo`, then the right range. The result's
  "declared stubs" line was the sign.
- plan: `declarationBefore`, `emptyNew`, `declaredAs` by `add_declaration`; the three heads by
  `replace_statements`. `emptyNew` took `Expression` and a cast at the lambda body; `change_type`
  to `ASTNode` dropped the cast (a block body would have thrown).
- plan: `segments` could not be folded (fold matches one return or void statements): four
  `replace_statements`.
- read: the layouts differed. Probed with `bin/jrefactor loop_to_stream` in a scratch project:
  `collected` kept `xs.stream().filter(s -> s.startsWith(` / `"a")).toList()` on one line, the
  flag form wrapped. Red golden `loop_to_stream_collect_multiline_stage`, then `layout` with the
  newline rule and `computed` and `grouped` through it.

## Measured

Not measured (no session-log hook; the CLI runs are in `JREFACTOR_LOG`). From the notes: every
Java change through `bin/jrefactor` (about 10 calls, most of them batches), one undo.

## Assessment

Through the tools. `fold` paid off only where the arguments were plain names; getter arguments
need a purity it does not have.

## Follow-ups

- The `forEach` rewrite builds its own head (`receiver + ".stream()"`) and wraps at its own call
  indentation; left as is.
- `fold` could treat a binding getter (`resolveBinding()`, `getInitializer()`) as pure.
