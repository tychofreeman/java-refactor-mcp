---
order: 389
item: 399
status: Done
---

## Item

`bin/session-report` judged which shell commands read or write Java with its own regexes, which disagreed with the read and write guards it measures: `nl`, `more`, `egrep`, `fgrep`, a reader after `cd` or `VAR=x`, and `install` or `ln` into a `.java` file were missed, and a `grep` whose pattern named a `.java` file, or a read under `resources/`, counted. The guard in turn took a quoted `>` (grep's pattern `'List<Path> x'`) for a redirection, so the read after it went unrefused (issue #519, code review)

## Status

Done 2026-11-14: `Guard.use(line, dir, write)` is the first simple command that reads (or writes) Java, with its file as written and resolved; `Guard.each` words the refusal from it, and `SessionReport` counts a Bash Java read, a Bash Java write's lines, and a Bash write's target by it. The report keeps its own regexes only for "any file written" (`touch`, `patch`, redirections). `Guard.redirection` tells a redirection word from text with a quoted `>`. `SessionReportTest.shellCommandsAreJudgedAsTheGuardsJudgeThem`, `GuardTest.aQuotedAngleBracketIsNoRedirection` (both failed before). Default to reopen: a shell write under `resources/` no longer counts as Java typed by hand (the guards allow it), while a Write of one still does; Read and Edit are judged as before

## Evidence

Issue #519; `docs/sessions/shell-classifier.md`
