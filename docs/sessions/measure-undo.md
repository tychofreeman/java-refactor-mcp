# Measure undo

- **Item:** the open cost in roadmap #44: every undo appeared to take
  2.7-2.9 s, about ten times the call.
- **Date:** 2026-11-04
- **Branch:** measure-undo

## Plan

No change to the tool. First find what the timer covers; `Journal.undo`
only reads the files it is about to restore and writes them back, so the
expected cost is file I/O. Then fix the timer, not the undo.

## Notes

- `read:` `Journal.undo` and `Journal.restore`, to see whether undo parses
  anything. It does not: it reads the current text of the changed files and
  writes the recorded text back.
- `read:` `GateCostTest` lines 60-90, to see what the undo timer covers.
- `plan:` the timer does not cover undo alone. `done` is taken right after
  the call, then the harness's own `assertEquals(TypeCheck.uncached(...))`
  runs before `Journal.undo`, and the undo timer is `undone - done`. The
  `uncached` check parses the whole project, which is where the 2.7-2.9 s went.
- `hand:` the timer change in `GateCostTest` (a test harness, not a tool;
  no tool edits test code here) was made by Edit, one line added and one
  changed.

## Measured

Same three calls as `measure-imports-stubs.md`, Commons Math 3.6.1, one run each,
after the timer fix:

| case | call | project parses | undo |
|------|------|----------------|------|
| baseline | 407 ms | 1 | 0 ms |
| import (`BitSet`) | 1102 ms | 1 | 0 ms |
| stub (`noSuchHelper`) | 535 ms | 2 | 0 ms |

The call times differ from the earlier run of the same calls (267, 959,
470 ms): single runs on this machine vary by a few hundred ms. Undo is under
1 ms in all three.

The session-log hook was not measured for this session.

## Assessment

- **Through the tools:** the only edit is test-harness code, made by hand.
  The harness is not a refactoring target, and the change is one line.
- **Did the plan hold?** The plan said the timer, not the undo, would turn
  out to be wrong. It did; no change to `Journal` was needed.
- **What went wrong:** the earlier record and PR #63 reported 2.7-2.9 s as
  undo. Both are now corrected in place (`measure-imports-stubs.md`, the
  design roadmap row, session notes). The mistake was in measurement, found
  by reading the timed section, not by measuring again.

## Follow-ups

- The call time for the same edit varies by hundreds of ms between runs.
  Figures under about 300 ms are not comparable without repeated runs. Not
  done: repeating runs needs the user's yes, as it costs time, not money.
