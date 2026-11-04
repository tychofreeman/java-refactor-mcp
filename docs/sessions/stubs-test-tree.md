# A stub for a test-only class goes in the test tree

- **Item:** roadmap #61's friction (`docs/sessions/stress-restore.md`, friction
  log 2026-11-04): `create_file` of a test declared the class the test uses,
  `StressSnapshot`, as a stub in `src/main/java`. Chosen by the user from
  "What's next?" on 2026-11-04 (the other options were closing order 43 and the
  A/B run, order 11, which costs money).
- **Date:** 2026-11-04
- **Branch:** stubs-test-tree

## Plan

The change, at the highest level: a stub is declared where its use is. A class
or member used only from test sources is declared in the test source root, not
the main one.

Operations, in order:
1. Reproduce (red golden): an edit in `src/test/java` uses an undefined class
   that nothing else uses; the golden expects the stub under `src/test/java`.
   Today it goes to `src/main/java`.
2. Change the stub's target file choice in `Stubs` to the source root of the
   file the use is in, when the project has one; main otherwise.
3. The one small edit that changes behaviour: that choice in `Stubs`.

Hypothesis, to confirm in step 1: `Stubs` picks the main source root for every
stub, whatever the use's file.

## Notes

- `plan:` the record was written before reading `Stubs`' target choice.
- **Step 1 (red):** `StubsTest.aClassUsedOnlyFromATestDeclaredInTheTestRoot`
  failed as the friction log said: `Helper.java` was written under
  `src/main/java`, and nothing under `src/test/java`. The red test was written
  with a heredoc (`hand:`, see below).
- **Step 2:** `Stubs.type` declares the class under the source root that holds
  the file of its use, falling back to the first root. Goldens give one root
  (`SourceProject.of`), so the case is a unit test, not a golden; this is a
  deviation from the plan's "golden" wording, recorded here.
- `hand:` the test file was written with a shell heredoc, and the one change
  to `Stubs.type` by a Python exact-match replace. The java-refactor-mcp tools
  are not in this session's tool list (no `mcp__jrefactor__*`), so `bin/jrefactor`
  was the alternative. Not tried. A `create_file` and a `replace_declaration`
  would have made both.
- `plan:` the golden became a unit test, as above.
- `refusal:` none.
- `bulk:` the test is about 30 lines of new test, written by hand (see above);
  the `Stubs.type` change is about 8 lines.

## Measured

Not measured: no session log for this branch (`.claude/session-log/stubs-test-tree.jsonl`
does not exist).
Verify: `./mvnw verify` passed, 198 tests, 0 failures, 4 skipped (the opt-in cost
tests). The `Malformed POM` lines in the output are the intended bad-pom case.

## Assessment

- **Through the tools or around them:** around. The test and the one change
  were written by hand, because the server's tools were not loaded in this
  session. The tools for both exist (`create_file`, `replace_declaration`). That
  is a session gap, the same as in `stub-after-delete-inherited.md`.
- **Did the plan hold?** Mostly. The red test came first and failed for the named
  reason. The golden became a unit test, since goldens have a single source root.
- **Reading:** `Stubs.type` and its callers (lines 262-280), and `SourceProject.of`,
  to see the roots a golden gets.
- **One change that would have helped:** the session starting with the server's
  tools loaded, as already recorded for `stub-after-delete-inherited.md`.

## Follow-ups

- A golden with two source roots would need a `SourceProject` option (a
  `roots` file in the case). Not built; the unit test covers the case.
- A class used from both main and test is declared under the root of its first
  use in the file the edit touched. Not reproduced; not covered.
