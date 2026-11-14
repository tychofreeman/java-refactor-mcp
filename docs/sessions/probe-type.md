# One probe-field type resolution

- **Item:** issue #493 (code review signatures #6), roadmap order 390 (#400)
- **Date:** 2026-11-14
- **Branch:** probe-type

## Plan

Reproduce the two suspected breakages first (`change_signature add_throws` on an enum method;
`subclass_to_strategy` `type` with a wrapped header). Then one `Jdt.probeType` that places the
field as a member, with a unit test of the placement; `replace_declaration` of the three copies
to call it, in one batch.

## Notes

- read: neither breakage reproduced through the CLI (enum with and without constant bodies and
  arguments, an exception nested in the enum, `java.sql.SQLException`; a wrapped header with
  `Function` and `Predicate`). JDT's recovery parsed the misplaced field into the type. So this is
  the consolidation alone; the test pins that the probed text now parses with no problem.
- refusal: `add_declaration` of the tests first: the stub typed from `probeType(...).getQualifiedName()`
  returns `Object`. Helper first, then the tests.
- plan: placement before the first member (or at the body's end, after `;` for an enum) needs no
  header walk, so `TypeParameters.headerEnd` went.
- read: a `-Dtest` run that printed nothing had not compiled the tests (a raw `List.stream()` warning under `-Werror`); my filter hid "COMPILATION ERROR". Fixed with a cast; the full verify ran every test.
- hand: none in Java.

## Measured

Not measured (the session-log hook is not loaded); CLI calls are logged to `JREFACTOR_LOG`.
Counts from the notes: 4 writing calls (one batch of 3 steps), 0 Java edits by hand.

## Assessment

Through the tools. The review's suspected failures were not real failures; the change is a
consolidation with a test of the shared helper.

## Follow-ups

None. `ParameterValue`'s probe (a local in a method body) is a different shape and stays.
