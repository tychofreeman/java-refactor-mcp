# Golden cases undo back to their input

- **Item:** roadmap #14 (order 12), "Golden cases also undo each applied step
  and compare with `in/`; incremental state compared with a fresh load". The
  roadmap's "Next"; evidence: in gorefactor this caught reloader bugs.
- **Date:** 2026-11-03
- **Branch:** golden-undo-check

## Plan

The change, at the highest level: every golden case that applies a change also
checks that undoing it returns the tree to its `in/` text, so a change that
cannot be undone fails its golden.

Derivation, in order:

1. The golden harness already runs each case through `Tools.call` and compares
   the output (`GoldenTest`). Measure which cases apply a change and which are
   refusals (`refused.txt`), from the case directories, before any code.
2. Red: add the undo check to the golden run, for applied cases only: call the
   `undo` tool once, then compare every file under the case's root with `in/`.
   Expect failures where a change is not fully journaled.
3. Green: fix each journaling gap the check finds, one at a time, least code
   first (a `Journal` entry or a restore path).
4. Refactor while green, only if the fixes leave duplication in `Journal` or
   `Gate` (the writers; roadmap #40 covers the duplicate restore).

Not in this item, unless step 2 shows it is needed: the fresh-load comparison
(incremental state against a fresh `SourceProject.load`). That needs a decision
about what state to compare; it is a follow-up.

Behavior change: none for the model; the tests get stricter.

## Notes

- `read:` the case counts, from the directories: 97 cases, 53 applied (51
  writes and two read-only `inspect` cases), 44 refusals. Question: which cases
  does the undo check apply to? Answer: the 51 non-read-only applied cases.
- `read:` `GoldenTest.java` and `TypeCheck.errors` (the cache), to see where
  the incremental state lives. The cache is `TypeCheck.LAST`, keyed by project
  and file texts.
- `plan:` the undo check passed on its first run: all 51 write cases returned
  to `in/`. So step 3 (fix journaling gaps) had nothing to do. The red was
  real only for the fresh comparison, which did not compile until
  `TypeCheck.uncached` existed.
- `bulk:` `TypeCheck.errors` was split so that the count is shared with
  `uncached` (about 20 lines moved, not rewritten): the same counting code, now
  in a `count` method. This was done by a scripted replacement, not by a tool.

## Measured

Not measured (the session log is not recording this session). Test results:
`./mvnw verify` passes, 127 tests; 2 are the opt-in cost tests and are skipped
without their property.

## Assessment

- **Through the tools or around them:** around them for the two code changes
  (the `GoldenTest` check and the `TypeCheck` split), which were written by hand
  and by one scripted replacement. No java-refactor-mcp tool was called.
- **Large writes:** none beyond the test and the moved counting code.
- **Did the plan hold?** The first two steps held. Step 3 had no work, because
  every write case undid cleanly. The fresh-count comparison is the part that
  needed the seam (`uncached`), which the plan did not foresee.
- **Reading:** the golden harness and the type-check cache, both needed.
- **One change that would have helped:** none specific; the gap was in the plan
  (the seam for the fresh count), which is recorded here.

## Follow-ups

- The fresh-load comparison here covers the type-check cache only. The import
  index (`Imports.LIBRARIES`) is also incremental state and is not compared; it
  is keyed by classpath and never cleared (roadmap #43).
