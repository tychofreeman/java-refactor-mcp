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
