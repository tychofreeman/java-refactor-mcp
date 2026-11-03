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

## Measured

Not measured (the session log is not recording this session).

## Assessment

## Follow-ups
