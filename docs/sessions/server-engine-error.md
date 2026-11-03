# Engine errors say where they failed; a failed undo does not hide them

- **Item:** roadmap order 35 (review row 42): "Server reports a non-refusal
  exception as 'engine error' with no stack trace and nothing on stderr;
  `Tools.call` calls `Journal.abort()`, which may throw and hide the original
  error." Chosen by the user on 2026-11-03 ("Do all 4 in order": 24, 37, 35, 40).
- **Date:** 2026-11-03
- **Branch:** server-engine-error

## Plan

Two defects, one PR, two red tests.

1. `Server.answer` (the engine-error path): the text names the exception and its
   first three frames, and the full trace goes to stderr. Red test:
   `ServerTest.anEngineErrorSaysWhereItFailed`.
2. `Tools.call`'s catch: an undo that throws `IOException` is added to the
   failure as suppressed, not thrown in its place. The helper is `Tools.abort`.
   Red test: `JournalTest.aFailedUndoDoesNotHideTheErrorThatCausedIt`.

Operations, in order:
1. Make `Server.answer` and its `Body` package-private (behaviour-neutral), so
   the test can call them.
2. Red: the engine-error test; red: the undo test, against a `Tools.abort` that
   does nothing (a stub, so the test compiles and fails on its assertion).
3. Green: the trace in the text and on stderr.
4. Green: `Tools.abort` attaches the undo failure; `Tools.call` calls it.

The behaviour changes: the engine-error text is longer (three frames), and the
full trace goes to stderr, which the protocol does not use.

## Notes

- `read:` `Server.answer`, `Tools.call`, `Journal.abort` (it throws
  `IOException` from `restore`).
- `hand:` `Server.java`, `Tools.java`, `ServerTest.java` and `JournalTest.java`
  changed with the Edit tool. The java-refactor-mcp tools were not in this
  session's tool list. Logged in the friction log.
- `refusal:` none.

## Measured

Not measured. No session log for this branch. Verify: 147 tests, 0 failures,
2 opt-in cost tests skipped. Both red tests failed for the stated reasons before
the fixes (`engine error ... java.lang.IllegalStateException: boom` with no
frame; `expected: <1> but was: <0>`).

## Assessment

- **Through the tools or around them:** around; four files by hand.
- **Did the plan hold?** Yes. The red tests failed for the reasons the plan named.
- **Red tests:** written first, both seen to fail.
- **One change that would have helped:** the java-refactor-mcp tools in the
  session.

## Follow-ups

- The stack trace goes to stderr for the server's log; no test checks stderr.
