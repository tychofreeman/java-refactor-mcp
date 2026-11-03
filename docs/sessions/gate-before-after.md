# The end-of-batch check moves into Gate

- **Item:** roadmap order 24 (review row 31): "the before/after type check goes
  through `Gate`: `Batch` and `Stubs` each re-implement it". Chosen by the user
  on 2026-11-03 ("Do all 4 in order": 24, 37, 35, 40).
- **Date:** 2026-11-03
- **Branch:** gate-before-after

## Plan

`Batch.run` with check "end" does what `Gate.apply` does, for a body of
steps instead of one edit: count the errors before, run the body with the check
deferred, let `Stubs.declare` declare what it needs, then refuse and restore, or
journal. The change moves that sequence into `Gate.checkedAtEnd`, returning the
body's result and the stubs. `Batch` calls it; the refusal wording stays the
same.

Operations, in order:
1. `Gate.Checked<T>` (result and stubs) and `Gate.checkedAtEnd(project, body,
   what, fix)`, taking the before count, the deferred body, the old texts from
   `Journal.written`, `Stubs.declare`, then `restore` or `journal`.
2. `Batch.run` calls `checkedAtEnd`; its own check and restore code go.
3. `Stubs.declare` stays where it is: it is the loop that declares stubs, and
   it is called from `Gate.apply` and `checkedAtEnd` alike. Its calls to
   `TypeCheck` inside the loop are not a second check.

The one behaviour change: a batch refusal also names the Maven classpath
problem when a name does not resolve, as an edit's refusal already does. That
is the same gate code, now used by both.

## Notes

- `read:` `Batch.run` (the end branch), `Gate.apply`, `Stubs.declare`, to find
  the shared sequence. Not read: the step bodies.
- `hand:` `Gate.java` and `Batch.java` changed with the Edit tool. The
  java-refactor-mcp tools were not in this session's tool list. `replace_text`
  would have made both edits. Logged in the friction log.
- `refactor:` `Gate.refusal` builds the one refusal used by `applyOrRefuse` and
  `checkedAtEnd`, so the Maven note is written once.

## Measured

Not measured. No session log for this branch. Verify: 144 tests, 0 failures,
2 opt-in cost tests skipped. The `batch` golden cases (check "end") and the
`edit` refusal goldens guard the refusal wording.

## Assessment

- **Through the tools or around them:** around, two hand edits in
  `Gate.java` and `Batch.java`.
- **Did the plan hold?** Yes, with one addition: the refusal builder became a
  shared helper, because `checkedAtEnd` would otherwise repeat the Maven note.
- **Behaviour:** the batch refusal gains the Maven classpath note when a name
  does not resolve. Stated in the plan.
- **Stubs:** not moved; its `TypeCheck` calls are the declare loop, not a second
  check. Order 24's "Stubs re-implements it" is therefore not fully addressed.
- **One change that would have helped:** the java-refactor-mcp tools in the
  session.

## Follow-ups

- The remaining open review items after this: order 37, 35, 40 (the user's
  queue, in that order).
