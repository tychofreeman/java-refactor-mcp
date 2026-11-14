# One helper for test projects on disk

- **Item:** issue #531 (code review core #23, group 5 of #532): test scaffolding repeated across
  test classes, and three tests the review found missing.
- **Date:** 2026-11-14
- **Branch:** test-projects

## Plan

A `TestProjects` class in the test root with `write`, `maven` and `copyTree`; `copyTree` comes
from `GoldenTest` by a delegate and `inline`, so its callers follow; the cited copies are replaced
with calls. The three missing tests are checked first: if the fixes of items 1, 2 and 11 brought
them, nothing is added.

## Notes

- read: the missing tests exist: golden `replace_text_refused_empty_old` (#462),
  `batch_refused_client_undo` (#473), `JournalTest.deletingTheOnlyFileKeepsTheProjectRoot` (#479).
- refusal: `create_file` of `TestProjectsTest` first declared the `TestProjects` stub in
  `src/main/java` (the main root, as for a production class); undone, and `TestProjects` created
  first in the test root with `create_file`, then the test, whose stubs went beside it.
- refusal: deleting the stub `TestProjects.copyTree` to `move` `GoldenTest.copyTree` onto it was
  refused (the test uses it); instead the stub was replaced with the body, `GoldenTest.copyTree`
  made a delegate, and `inline` of it rewrote its two calls (`GoldenTest`, `ProbeTest`).
- tools: two `create_file`s, an `undo`, a batch of five steps (copy, delegate, inline, write,
  maven), a batch of five for the call sites (three `replace_text`, two `replace_declaration`).
  No Java by hand.

## Measured

Not measured: the session-log hook is not loaded here. 0 hand edits of Java.

## Assessment

Through the tools; two refusals, both worked around with tools. The test did not run red first:
the class was created with its stubs by the tool, and the implementations went in in the same
batch as the move.

## Follow-ups

- The other `createDirectories` and `writeString` pairs in about 20 test classes.
- `Stubs.TEST_DOUBLE` could take `*Projects`/`Test*` names (friction log).
