---
order: 480
item: 490
status: Done
---

## Item

`LoopToStream` repeated its scaffolding across its rewrites: the "one local declared just before the loop" check (`collected`, `resultBefore`, `grouping`), the empty `new ArrayList<>()`/`new HashMap<>()` test, the text before an initializer (five times), the stream head and stages (five), the count of a variable's uses (five), and the line layout three ways: `collected`, `total` and `search` kept a stage that spans lines on one line with the rest (`xs.stream().filter(s -> s.startsWith(` then `"a")).toList()`), where `computed` and `grouped` put each segment on its own line (issue #516, code review)

## Status

Done 2026-11-14: `declarationBefore`, `emptyNew`, `declaredAs`, `beforeInitializer`, `segments` and `uses`; every rewrite but `forEach` (its own call indentation) lays out through `layout`, which now puts each segment on a line of its own when one spans lines. Golden `loop_to_stream_collect_multiline_stage` (failed before). Default to reopen: the one layout rule is `computed`'s and `grouped`'s, over keeping each rewrite's rule (the smaller output change: it moves only a pipeline whose stage spans lines)

## Evidence

Issue #516; `docs/sessions/loop-stream-scaffolding.md`
