# A text argument from stdin on the command line

- **Item:** the user, 2026-11-05: cloud sessions feed `bin/jrefactor` through Python
  heredocs or scratch files to pass multi-line text; let the CLI read a text argument
  from stdin instead.
- **Date:** 2026-11-05
- **Branch:** cli-stdin-argument

## Plan

A `key_file` argument whose path is `-` reads that argument's text from stdin, so a
shell heredoc gives `content`, `new` or `old` directly
(`bin/jrefactor add_declaration file=... content_file=- <<'EOF'`). One argument per
call can come from stdin, and not together with a JSON object on `-`; a second is
refused, naming the file form for it.

Derivation: a `CliTest` case first (red); then in `Cli.withFiles` the `-` path read
from stdin (`replace_text` of the one line that reads the file, plus the
second-stdin refusal); then `Cli.USAGE`, `CLAUDE.md`'s CLI paragraph and the README.

## Notes

- read: `Cli.arguments`, `readJson`, `withFiles`, and the call in `run`, to place the
  stdin read.
- refusal: the test's first `add_declaration` imported `UTF_8` from `sun.nio.cs`;
  resent with `StandardCharsets.UTF_8`, which the file imports.
- refusal: the batch changing `withFiles`'s signature missed its second caller (in
  `run`); found by `inspect` `pattern`, added as a third step.
- A Python heredoc editing the README ended early at the README example's own `EOF`
  line, and the shell ran the rest, `bin/jrefactor undo` among it (refused, nothing
  undone); the docs were then edited with Edit. A session-notes trap now says so.

## Measured

Through jrefactor: one `add_declaration` (the test), two batches (5 edit steps), one
`replace_text` (the usage text); refused 2, answered with another call each; no Java
by hand. Docs by Edit.

## Assessment

Through the tools; the only text written was the new behavior and its test. The plan
held, with one more call site than the outline showed (`run` also calls `withFiles`).

## Follow-ups

- Several text arguments from one stdin (multi-line `old` and `new` for
  `replace_text`) are not covered: one goes on stdin, the other in a file, or the
  call as JSON on `-`.
