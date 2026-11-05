# Triage the friction log

- **Item:** check each `docs/friction-log.md` entry against the code and the roadmap;
  mark those a later PR closed, and put the open gaps on the roadmap. Chosen from
  "what's next?" (70 entries, 12 marked done; the roadmap is ordered by this log).
- **Date:** 2026-11-05
- **Branch:** friction-triage

## Plan

The log should say, for each entry, whether it is still friction: closed (by which
PR or roadmap row), open (on which roadmap row), or not to be fixed (and why). The
derivation: list the entries without a marker; for each, find the PR or row that
answers it from `docs/design.md`, the session records and `git log`, and check the
code through `inspect` `pattern` or `names` where a record is not conclusive;
append the marker line to the entry (`Follow-up: done, #N`, `Open: roadmap #N`,
or `Not fixed: why`). Gaps still open and on no row become roadmap rows, ordered
by how often the log names them. No Java changes.

## Notes

- read: the whole log (739 lines, by Read, in three parts): the item is the log.
- read: `Imports.toImport` and `staticImports`, `Edits` around "old is not in", and
  the members-do-not-parse refusal in `Declarations`, through `inspect`, to tell
  which ideas the code already has.
- Probes on a scratch project through `bin/jrefactor`: a stub for `B.MAX` (refused,
  no stub), a stub replaced by erasure (refused, clash), an import for a member a
  later batch step declares (`javax.management.Query` imported), `create_file` with a
  compile error (no batch advice), `inspect` with no arguments (refused), unparseable
  members (no line).
- refusal: the read guard refused a `cat` of the scratch `B.java` and a `grep` of
  `ServerTest.java`, and the write guard a `printf` of the scratch `A.java`; answered
  with `inspect`, `inspect` `pattern` and `create_file`. All three were right.
- hand: the markers and rows were written by Python over the two Markdown files, a
  bulk edit by heading that `replace_text` would have taken 59 calls for (or one
  batch of 59 steps).
- plan: the markers follow the log's own header (`Follow-up: done`, `Follow-up:
  open`, `Follow-up: no gap to fix`) rather than the three forms planned; one more
  fix than planned: a Done marker in the wrong entry (the
  `--rebuild` one, under "a batch removes an import").

## Measured

`bin/session-report` for the branch: 29 tool calls; through jrefactor 0 refactoring
and 0 edit steps (no Java changed); 11 `bin/jrefactor` calls in Bash (reads and
probes); 2 refused by jrefactor (the probes' expected refusals); by hand 1 (the
Write of this record); 4 denied (the three guard refusals above, and the report
itself once); reads: Read 3 (the log).

## Assessment

- **Through the tools or around them:** no Java changed. The two Markdown files
  were edited by script, which suits 59 markers placed by heading.
- **Reading:** the code questions were answered by `inspect` and by probes, not by
  reading bodies whole; the probes were cheaper and more conclusive than reading
  `Stubs`.
- **Result:** of 70 entries, 12 were already marked; 59 now have a follow-up: 23 say
  done (with the PR), 22 say no gap to fix (a session without tools, or a tool being
  built), 1 none needed, and 13 point to open rows, some with a done part. The open
  gaps are seven new rows (roadmap #105-#111), the first of which, ambiguous
  imports, six entries name.
- **One change that would have helped:** a marker convention used from the start;
  the log's header asked for *Follow-up: done*, and later entries wrote `Done:`.

## Follow-ups

- Roadmap #105 (ambiguous imports) has the most evidence of the new rows.
