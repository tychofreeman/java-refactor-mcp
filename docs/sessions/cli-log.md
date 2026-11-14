# An opt-in log of bin/jrefactor runs

- **Item:** roadmap #265 (order 255; first numbered 264, which #555 took at the same time), the owner's request of 2026-11-14: measure agents that
  call the tools through `bin/jrefactor` in sessions without the Claude Code hooks.
- **Date:** 2026-11-14
- **Branch:** cli-log

## Plan

`Cli.main`, when `JREFACTOR_LOG` names a file: keep stdin when an argument is `-` (put it back
for `run`), copy what `run` prints while printing it, then append `Cli.logLine(...)`, a
hook-shaped input handed to `SessionLog.entry`, inside a catch-all. `logLine` writes the command
as `bin/jrefactor <args>`, each JSON argument replaced by its object on one line (sections
filled in, `;&|'` as JSON escapes, since `SessionReport`'s command pattern stops at `;&|` and its
word splitter at a quote). Test first: `CliTest` checks the line's fields and that
`SessionReport.report` counts the calls, the refusal and the refactoring step.

## Notes

- plan: test by `add_declaration` (stubbed `Cli.logLine`), red; then one `batch` of
  `replace_declaration` (`main`, `logLine`) and `add_declaration` (helpers).
- refusal: none. The first `add_declaration` used `JSON` (not a field of `CliTest`) and a bare
  `null`: it stubbed a class `JSON` and two overloads; `undo`, then the test with
  `SessionLog.JSON` and `""`.
- hand: none in Java; docs by hand.
- read: `SessionLog`, `Cli`, `SessionReport`'s CLI parsing read with `cat`/`sed` (the read guard
  is not loaded here).
- check: end to end on a scratch project: a rename, a stdin JSON with sections and `; | &` in
  code, a refusal, and an unwritable log path (no change to output or status); the report read
  the file as 3 calls, 1 refused, 1 refactoring and 1 edit step.

## Measured

Not measured: this session has no session log (the hook is not loaded).

## Assessment

Through the tools: two calls wrote all the Java (one undone). Reading went around `inspect`.

## Follow-ups

- A `key=value` word holding `;`, `&` or `|` still cuts `SessionReport`'s command pattern there;
  rare, since text goes through `key_file`.
