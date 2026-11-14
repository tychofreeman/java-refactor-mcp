# inline of a local with an array initializer

- **Item:** issue #410, found by the Commons Lang stress run (`docs/sessions/stress-new-options.md`);
  roadmap #178 (order 171), the owner's request of 2026-11-13 to fix the run's defects
- **Date:** 2026-11-13
- **Branch:** fix-inline-array-initializer

## Plan

Two defects. `InlineVariable` copies the initializer's text to each use, and an array initializer
is an expression only in a declaration: write `new <declared type> ` before it (the dimensions
after the name included), in parentheses where the use is indexed (`new int[] {1}[0]` does not
parse). Then the refusal: find where "does not parse ... Fix the syntax" is written for a
refactoring's own output, and make it say the tool wrote it. Behavior: a new array at each use
changes identity when there are several uses; follow the rule `inline` already has for `new`
(several landings of code that creates are refused by `Behavior.flow`), not a new one.

## Notes

- read: the wording is `Edits.parses`; the refactoring's case came from `Edits.gated`, where
  `FromFields.complete` parsed the edited text under `path.toString()`, the absolute path. Every
  refactoring through `Edits.gated` shared it (17 tools); those writing through `Gate.applyOrRefuse`
  alone get the gate's compile refusal instead.
- read: `Effects` records `new Foo()` as `CREATE`, which `changes()`, so several landings are
  refused; `new int[]` is `NEW_ARRAY`, which did not, so `int[] a = new int[] {1, 2}` used twice
  was inlined silently into two arrays. An initializer alone was not recorded at all.
- plan: the several-landings refusal now also covers `NEW_ARRAY` (`Effects.newArraysNow`), and an
  initializer alone is a `NEW_ARRAY`. Not by making `NEW_ARRAY` count in `changes()`: that would
  change every place-and-crossing rule for every tool that moves code.
- read: a first golden of `new int[]` used twice with `counts[0]++` between was already refused,
  by the read-past-a-write rule; rewritten with two reads only.
- refusal: `bin/jrefactor replace_text file=... -` refused (`-` is a whole JSON object, not one
  argument); sent as `bin/jrefactor -` with `"tool"`.
- hand: the tool contract's two paragraphs by a Python string replace; the design row, the code
  and the tests through `bin/jrefactor` (`batch`, `replace_text`, `add_declaration`). Golden
  files written as plain files (resources).

## Measured

Not measured: the session-log hook is not loaded here. From the notes: 5 tool calls for code and
tests (two batches), one refusal of my own argument shape, one hand edit of a doc.

## Assessment

Through the tools for every Java change. The plan held; the one surprise was that `new int[]`
used twice had no rule at all, which the fix closes with the `new` object's refusal.

## Follow-ups

- Whether an array creation used in several places should be reported rather than refused (a
  default chosen here, in the roadmap row).
- `Edits.parses` callers that pass a relative path are fine; any other refactoring that builds
  text outside `Edits.gated` and parses it with `Edits.parses` would still say "Fix the syntax".
