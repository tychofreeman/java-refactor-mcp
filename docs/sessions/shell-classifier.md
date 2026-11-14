# Guard and SessionReport judge shell commands alike

- **Item:** issue #519 (code review, group 4 of #532): the session report's own regexes for shell
  reads and writes of Java disagreed with the read and write guards it measures.
- **Date:** 2026-11-14
- **Branch:** shell-classifier

## Plan

One test of "does this Bash line read (write) Java", used by both. Make the change easy first:
split `Guard.each` into `Guard.use` (the first command that reads or writes Java, with its file
as written and resolved) and the refusal wording over it. Then a report test with commands the
two judged differently (red), and the report's `readKind`, `handLines` and Bash `writes` calling
`Guard.use`; its `READS` regex goes, `WRITES` and the redirect targets stay for "any file".

## Notes

- plan: `replace_declaration` of `Guard.each` and `add_declaration` of `Use` and `use`, one batch.
- plan: red: `SessionReportTest.shellCommandsAreJudgedAsTheGuardsJudgeThem` (nl, more, `cd && cat`
  counted; a grep pattern naming a .java file and a read under resources not; `install` into a
  .java file by hand).
- plan: `add_parameter` `cwd` to `readKind` and `handLines` (value `cwd` at the call), then
  `replace_declaration` of the three methods and of `READS` (empty: deleted), one batch.
- refusal: none. The existing `aGrepWithAngleBracketsInItsPatternIsARead` then failed: the guard
  took the quoted `>` of `'List<Path> x'` for a redirection, so the report test had pinned a case
  the guard missed. Red `GuardTest.aQuotedAngleBracketIsNoRedirection`, then `Guard.redirection`
  (one pattern for the words `commands` makes of a redirection) through `replace_text` and
  `add_declaration`.
- read: `inspect` outlines, ranges and `pattern` only.

## Measured

Not measured (no session-log hook here; `JREFACTOR_LOG` holds the CLI runs). From the notes:
5 `bin/jrefactor` write calls (3 batches, 2 single edits) for the Java, plus `replace_text` for the
docs; nothing by hand.

## Assessment

Through the tools throughout. The report's test caught a guard bug the guard's own tests had not:
a case for each divergence, run through both, is what the consolidation bought.

## Follow-ups

- `patch` and `touch` of a `.java` file are writes the guard does not judge (the report counts them
  as writes of some file). Adding them to `Guard.writes` is a change to what the guard denies: for
  the owner.
- Read and Edit of a `.java` file under `resources/` still count as Java in the report, unlike the
  guards; `Guard.javaSource` would make them agree.
