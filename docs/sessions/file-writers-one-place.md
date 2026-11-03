# One writer for project files

- **Item:** roadmap order 40 (review row 47): "`Gate.restore` and `Journal.restore`
  both write or delete files, with different parent-directory handling;
  `Gate.put` is a third writer." The review's fix: one writer in `Journal`,
  used by `Gate`. Chosen by the user on 2026-11-03 ("Do all 4 in order": 24, 37,
  35, 40).
- **Date:** 2026-11-03
- **Branch:** file-writers-one-place

## Plan

`Journal` becomes the one writer: `put(file, text)` (write, creating the
directories, or delete for null) and `restore(texts)`, a loop of `put`. `Gate`
calls them; `Gate.put` and `Gate.restore` go.

Operations, in order:
1. Red: `GateTest.aRestoreRecreatesTheDirectoryOfTheFileItWrites` calls
   `Gate.restore` on a file whose directory is gone. It failed with
   `NoSuchFileException`, because `Gate.restore` never creates directories.
2. `Journal.put` and `Journal.restore` (package-level, `put` the one writer).
3. `Gate.write`, `Gate.apply` and its restores call `Journal.put` and
   `Journal.restore`; `Gate.put` and `Gate.restore` are removed. The test now
   calls `Journal.restore`.

The one behaviour change: a restore whose directory is gone now recreates it,
as `Journal` always did. Before, `Gate`'s restore failed in that case. Nothing in
the gate reaches it today (the directory existed when the old text was read),
so it is a guard, not a fix of a reachable bug.

## Notes

- `read:` the three writers (`Gate.write`, `Gate.put`, `Gate.restore`) and
  `Journal.restore`, to compare the parent-directory handling.
- `hand:` `Gate.java`, `Journal.java` and `GateTest.java` changed with the Edit
  tool and sed (the sed for the call sites). The java-refactor-mcp tools were
  not in this session's tool list. Logged in the friction log.
- `refusal:` none.
- The restore test sits in `GateTest` but calls `Journal`; it could move to
  `JournalTest`, which is a naming point, not behaviour.

## Measured

Not measured. No session log for this branch. Verify: 148 tests, 0 failures,
2 opt-in cost tests skipped. The red test failed first with `NoSuchFileException`.

## Assessment

- **Through the tools or around them:** around; three files by hand, one of
  them by sed.
- **Did the plan hold?** Yes. The red test failed as the plan said.
- **One change that would have helped:** the java-refactor-mcp tools in the
  session.

## Follow-ups

- Nothing left of order 40. The `Signatures` and text-application items of
  order 32 are unchanged.
