# Nested deferred calls keep the outer one deferred

- **Item:** roadmap order 41 (review row 48): `Gate.deferred` resets the flag
  to false in `finally`, not to its previous value, so a nested deferred call
  ends the outer deferred call early. Chosen by the user on 2026-11-03 from
  the next-item options.
- **Date:** 2026-11-03
- **Branch:** gate-deferred-nesting

## Plan

Save the flag's value on entry and restore that value in `finally`. The change
is one local and one assignment in `Gate.deferred`. The test comes first: a
deferred call that runs another deferred call, and then reads the flag.

Operations, in order:
1. `GateTest.aNestedDeferredCallLeavesTheOuterOneDeferred` (red: the flag is
   false after the inner call).
2. `Gate.deferred` saves the previous value and restores it.

The one edit that changes behavior: a nested deferred call no longer clears
the outer deferred flag. A batch with check "end" that contains a
deferred call would have run its later edits with the check on; now it stays
deferred.

## Notes

- `plan:` the summary's review row named this defect; the test was written
  first, and it failed with `expected: <true> but was: <false>` before the fix.
- `hand:` `Gate.java` and `GateTest.java` changed with the Edit tool. The
  java-refactor-mcp tools were not in this session's tool list. Logged in the
  friction log.
- `refusal:` none.

## Measured

Not measured. No session log for this branch
(`.claude/session-log/gate-deferred-nesting.jsonl` does not exist). Verify:
144 tests, 0 failures, 2 opt-in cost tests skipped.

## Assessment

- **Through the tools or around them:** around. Two hand edits, one red test,
  one fix. `replace_text` would have made the fix; `add_declaration` would
  have made the test.
- **Did the plan hold?** Yes. The red test failed for the stated reason.
- **Red test:** written before the fix and seen to fail; the fix made it pass.
- **One change that would have helped:** the java-refactor-mcp tools in the
  session, so the edits went through the server.

## Follow-ups

- Batch's and Stubs' own before/after checks (order 24) are unchanged.
