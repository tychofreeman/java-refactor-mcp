# No static mutable state

- **Item:** roadmap #33 (order 26) and #43 (order 50), "static mutable state"
  and "process-global state cannot be isolated by tests". From the code review
  of 2026-11-03. The user asked for both to go, 2026-11-03.
- **Date:** 2026-11-03
- **Branch:** no-global-state

## Plan

The change, at the highest level: each project keeps its own state, and each
started server keeps its own call state, so nothing is shared between two
projects or two servers in one process.

Derivation, in order:

1. Red: `JournalTest.aProjectOpenedAgainHasNoHistory`. An undo through a new
   project over the same folder undid the first project's change, because
   `Journal.DONE` was keyed by the root in a static map. It failed.
2. Green: `Session` (one per `SourceProject`, a record component) holds the
   undo deque, the open call, the deferred flag, the library index and the last
   type check. `Journal`, `Gate.deferred`, `Imports.index` and `TypeCheck` use
   it. `Server.Quiet` replaces `ONE_AT_A_TIME`, `CALLS` and `lastOutput`; `main`
   and `start` pass it along.
3. Refactor while green: none beyond the signatures, which now take the project.

Behavior changes, model-facing: none in the tools' results. A second project
over the same folder starts with no undo history, and rebuilds the library
index once on its first use.

## Notes

- `refusal:` the red test failed as planned (no refusal: the undo succeeded).
- `hand:` the code changes were Python scripts replacing anchor text in eight
  files; `replace_text` would have made them. The friction-log entry names it.
- `hand:` `Stubs.DECLARED` (also in the roadmap row) was already gone (#30).
- `read:` `Journal` and `Server`'s call and quiet logic, to see every use of
  the state.

## Measured

Not measured (the session log is not recording this session).

## Assessment

Through the tools: the red test ran through `JournalTest`. The change itself
was hand editing of the server's own source, as in the earlier items.

Large writes: none; `Session` is new and about 30 lines, the rest are edits of
signatures.

One change that would have helped: the same as the earlier items, the server's
own Java edited through `replace_text`, which the instructions ask for once the
server runs.

## Follow-ups

- The remaining static state is constants only (`ObjectMapper`, `Set.of`).
- `TypeCheck` and `Imports` cache per project now; if the server ever serves
  several projects at once, the caches are per project already.
