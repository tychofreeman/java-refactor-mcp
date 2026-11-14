# Run one golden case or probe

- **Item:** roadmap #195 (order 188): the owner's follow-up of `docs/sessions/move-record-fields.md`,
  where a scratch script ran one golden case through the CLI and diffed it, since `GoldenTest`
  ran every case.
- **Date:** 2026-11-13
- **Branch:** golden-one-case

## Plan

Prefer a standard mechanism if one works: both `GoldenTest` and `ProbeTest` are a `@TestFactory`
of one `DynamicTest` per directory, named by it. Try Surefire's `-Dtest=GoldenTest#<case>` first.
If it does not select dynamic tests, the one change is the list of directories the factory walks:
a test-side `Cases.select(root, property, patterns)` (unit test first), then both factories call
it with their own system property.

## Notes

- read: `-Dtest=GoldenTest#add_case` ran 0 tests; `GoldenTest#cases` ran all 796;
  `GoldenTest#cases*add_case*` ran 0. Surefire's method filter matches the factory method only, and
  JUnit's display-name filters are not reachable through Surefire's options. So new code.
- plan: `Cases` written first as a stub (`create_file`) in the test root, since a stub the edits
  declare for a class goes in the main root unless its name reads as a test double; then
  `CasesTest` (`create_file`), red, then `replace_declaration` of `Cases.select`, green.
- refusal: a `batch` step with its arguments beside `tool` (not under `args`) was refused as
  "replace_text needs file"; resent under `args`.
- Both factories changed by one `batch` of two `replace_text` steps; the class docs likewise.
- Checked: `-Djrefactor.golden=add_case,rename_local*` ran 2 cases, `=nosuch` failed naming the
  pattern and the property, `-Djrefactor.probe=add_parameter_*` ran 6 probes.
- hand: docs only (Markdown).

## Measured

Not measured: the session-log hook is not loaded in this session. Java written: 3 `create_file`
or `replace_declaration` calls and 2 batches, all through `bin/jrefactor`; no hand edits of Java.

## Assessment

Through the tools. Defaults to reopen (the owner said take the smallest): one property per test
class (`jrefactor.golden`, `jrefactor.probe`) over one shared property, so running both classes with
one property set cannot fail the other for a name it does not have; a run fails when any pattern
matches nothing, over only when none does, so a typo among several names is not passed silently.
Patterns are split on commas, so a glob's `{a,b}` cannot be used.

## Follow-ups

- None needed. Agents' scratch scripts for one case (such as `/home/claude/scratch-mrf/case.sh`)
  can be dropped in favour of the property.
