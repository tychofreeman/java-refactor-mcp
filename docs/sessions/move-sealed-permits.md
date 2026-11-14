# `move` within a sealed hierarchy with implicit permits

- **Item:** issue #441 (the Java 17 stress run, `docs/sessions/stress-enum-switch.md`), roadmap #190
- **Date:** 2026-11-13
- **Branch:** move-sealed-permits

## Plan

A sealed type without `permits` permits the subtypes of its own file; once the move splits them,
write the implicit list as an explicit clause, which keeps the meaning. Derivation: in
`MoveType.run`, beside `qualifyAndWiden`, find each sealed type of the outer file with no clause,
its direct subtypes in the file, and whether the moved range splits them; if so insert
` permits A, B` after the header (into the moved text or the outer file, by where the sealed type
is), widening a private subtype named from the other file. Replace the gate's fix line, which spoke
only of a clash. Goldens first: the sealed type moved, a subtype moved (with a private sibling), a
generic sealed type; a probe with a pattern switch over the moved sealed type.

## Notes

- read: `MoveType` through `inspect` (`pattern`, then the two ranges to change); the shape of
  `run` was known from #440's branch.
- plan: names from the package (`Shapes.Circle`), not the shortest in scope, so one rule serves
  the moved file and the outer file alike: a default to reopen.
- bulk: one `batch` (five steps) wrote all of the Java; formatting by Spotless.
- hand: golden and probe files (resources, plain files); the docs.

## Measured

Not measured: the session-log hook is not loaded in this session. One tool call carried every
Java change.

## Assessment

Through the tools; the plan held, and the batch applied first time.

## Follow-ups

- Across packages in a named module the clause would name another package's types: #438.
