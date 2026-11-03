# Maven classpath failure is named in refusals

- **Item:** roadmap #38 (order 31), "A failed Maven classpath is only on
  stderr". From the full code review of 2026-11-03, read, not run.
- **Date:** 2026-11-03
- **Branch:** maven-classpath-note

## Plan

The change, at the highest level: the model sees why library types do not
resolve, in the refusal that comes of it, not only on the server's stderr.

Derivation, in order:

1. Option choice (asked): keep serving and name the cause, over refusing to
   start. The user chose keep-serving.
2. Red: `GateTest` builds a project whose classpath failed and refuses an edit
   with an unresolved import. It needs a fourth `SourceProject` component (the
   problem), so the first run was a compile error, then an assertion failure.
3. Green: `Gate.applyOrRefuse` appends the problem when an introduced error
   says "cannot be resolved".

Behavior change, model-facing: such a refusal gains one line: "Maven gave no
classpath, so library types do not resolve: <reason>".

## Notes

- `refusal:` the red test's first run failed on the expected text: my assertion
  had a colon where the message has a comma. Fixed the test's string, not the
  code.
- `hand:` `SourceProject.java`, `Gate.java` changed with the Edit tool. See the
  friction-log entry of this date.

## Measured

Not measured (the session log is not recording this session).

## Assessment

Through the tools: the reproduction is a unit test, which is not a golden case,
because golden projects are not Maven projects. The plan was written after the
red test, not before it, which the whats-next skill asks against; this record
says so.

Large writes: none.

One change that would have helped: a golden option for a project whose
classpath failed (a `classpath_problem` in `args`), so the case runs through the
harness. Not built: one unit test covers the one behavior.

## Follow-ups

- The server's stderr line stays as it was; the model now sees the same reason
  in refusals.
