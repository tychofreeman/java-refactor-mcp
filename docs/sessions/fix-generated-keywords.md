# Generated names that are keywords

- **Item:** issue #480 from the 2026-11-13 code review (roadmap row 262, order 252)
- **Date:** 2026-11-14
- **Branch:** fix-generated-keywords

## Plan

Reproduce both keyword bugs with a golden case each. Then one place that picks a free name:
move `LoopToStream.unused` to `Names` (as `fresh`), extract its two halves (`takenIn`, `free`),
and give `free` the keyword test; `elementName` falls back to `item` for a keyword; Skeleton's
name-from-type moves to `Names.parameterName` with Stubs' guard. Other agents own `Stubs`,
`ThrowsCallers`, `TestFor`, `InlineFunction` and `Build`: their loops stay.

## Notes

- read: both goldens failed as expected (`for (int int : ints)`, `apply(Long long, Boolean boolean)`),
  each refused as not parsing.
- plan: `move` LoopToStream.unused to Names (6 calls in 2 files), `rename` to fresh, two
  `extract_function`s (free, takenIn, visibility package), `inline` of the local, `rename` of the
  parameter; then a `batch` of `replace_text` for the keyword test and doc comments, and a
  `batch` of `add_declaration` (parameterName) and `replace_statements` in Skeleton.
- refusal: `rename` with `name=Names.unused` and no `line` asked for a line; given, it passed.
- refusal: a batch step's `old` was a doc line I had misremembered; `retry` fixed that step alone.
- hand: the golden resource files (plain files, as the brief says).

## Measured

Not measured: the session-log hook is not loaded here. Every Java change went through
`bin/jrefactor` (8 calls, two of them batches); none by hand.

## Assessment

Through the tools. The plan held. `rename` of a method named `Type.method` could find it
without a line, as `move` does; minor, not logged as friction since the refusal said what to send.

## Follow-ups

- The fresh-name loops in `Stubs`, `ThrowsCallers`, `TestFor`, `InlineFunction`, `Build` and
  `Surround` (#481 takes Surround and ThrowsCallers' catch variable) can call `Names.free`.
