# Stub import picks per file

- **Item:** roadmap #37 (order 30), "`Stubs.uses` gives every file the import
  picks of every stub". From the full code review of 2026-11-03, read, not
  run; this item reproduces it first.
- **Date:** 2026-11-03
- **Branch:** stubs-uses-picks

## Plan

The change, at the highest level: each file's import picks are the names the
stubs written into it use, not the names of every stub in the round.

Derivation, in order:

1. Red: golden `stubs_uses_per_file`. `Shop.java` is created with
   `cart.take(java.awt.List)` and `other.drop(java.util.List)`. Expect the stub
   for `Cart.take` to take `java.awt.List`; it took `java.util.List`, and the
   call was refused.
2. Green: `Round` keeps `wantedUses` per target file, filled in `want` from
   `uses`, which each stub handler clears at its start; `write` picks from
   that file's set.
3. Refactor while green: none planned.

Behavior change, model-facing: a stub no longer takes an import from another
stub's name, so a shared simple name resolves to the class the stub uses.

## Notes

- `hand:` `Stubs.java` changed with the Edit tool (the per-file set, the
  clears, the `write` picks). The tool that would have done it: `replace_text`
  on this file; not used. Same gap as `docs/sessions/overload-generic-params.md`.
- `refusal:` the red golden was refused: "The method take(java.util.List) ...
  is not applicable for the arguments (java.awt.List)". That is the
  reproduction. A first attempt with `java.sql.Date`/`java.util.Date` was not a
  refusal: `java.sql.Date` extends `java.util.Date`, so the wrong import
  compiled. Changed to two unrelated classes.
- `hand:` the golden's `out/` was written from the tree the green change
  produced after checking each file by eye: `Cart.java` imports `java.awt.List`,
  `Other.java` imports `java.util.List`.

## Measured

Not measured (the session log is not recording this session).

## Assessment

Through the tools: the reproduction ran through the golden harness. The
change was an Edit-tool edit of `Stubs.java`, not a tool call; the repo's own
source was not edited through `replace_text`.

Large writes: none beyond the small edits.

Reading: `Stubs.Round` (`find`, `method`, `constructor`, `want`, `write`) and
`Imports.fix`'s picks, to see where the set was filled and read.

One change that would have helped: the same as #36, the repo's own Java
through the edit tools, which the instructions already ask for once the server
runs. The friction-log entry names it.

## Follow-ups

- The review items #38 (failed Maven classpath silent) and the rest remain
  open on the roadmap; the next "What's next?" offers them.
