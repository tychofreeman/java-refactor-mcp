# Stubs at the end of a batch, attributed to the step that made the name

- **Item:** order 62 (row 55): a batch checked at the end declares stubs for
  every unresolved name, including names a refactoring step left, not only
  names an edit step wrote. Assigned to the cloud session by the split on
  `main`. The user chose "attribute names to steps" (2026-11-03, the
  recommended form of the choice set).
- **Date:** 2026-11-04
- **Branch:** batch-stubs-by-step

## Plan

The change, at the highest level: a name a refactoring left unresolved is
refused at the end of the batch, a name an edit wrote is stubbed as before.

Operations, in order:

1. Record, per refactoring step of a batch with check "end", the error keys it
   introduced (`TypeCheck.introduced` before and after the step, as
   `file | message`).
2. Keep those keys in the session, while they remain at the end.
3. `Stubs.Round.find` declares no stub for a problem whose key is in that set,
   so the end check refuses it with the existing wording.
4. Red first: a golden or unit test in which a refactoring leaves a name
   unresolved and a later edit does not fix it.

## Notes

- `read:` `Batch.steps`, `Gate.checkedAtEnd`, `Stubs.declare`, `TypeCheck.errors`
  and `introduced` (the error key is `file | message`, no line numbers).
- `refusal:` none. Step 4 could not be made red, which stopped the work (below).
- `tool:` `bin/jrefactor move_type` on a scratch project (`Outer.Inner` imported
  as `p.Outer.Inner` from another file): the move rewrote the import, 1 reference
  in 1 file, no error left. So the friction-log case is the one fixed earlier
  (qualifying inherited names, `move_type-inherited`).
- The gate refuses a refactoring whose output adds compile errors unless the
  batch is deferred, so a refactoring leaves a name unresolved only when a later
  edit is meant to fix it. No current refactoring does that in the cases tried.

## Measured

Not measured: no session log for this branch. Counted from the notes: 1
`bin/jrefactor` call on a scratch project, 0 hand writes of Java, 0 code changes.

## Assessment

- **Through the tools or around them:** the one check was through `move_type`.
- **Did the plan hold?** No: step 4 has no reproduction, so steps 1-3 were not
  built. Building them without a failing case would break the TDD order in
  CLAUDE.md.
- **One change that would have helped:** a refactoring that can be deferred and
  left incomplete (a tool whose output the end check completes) would give the
  case a test; no such tool exists today.

## Follow-ups

- Decision for the user: close row 55 as not reproducible (the mechanism above
  waits for a refactoring that leaves a name for a later edit), or build it
  without a red test. Not decided here.
