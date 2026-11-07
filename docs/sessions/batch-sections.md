# Code sections in the CLI's JSON

- **Item:** the user asked for gorefactor's code sections (its #606) here too: text after the
  JSON object on stdin or in an `@file`, in `@@name` sections, that a whole JSON string
  `"@name"` stands for, so code goes into a batch without escaping.
- **Date:** 2026-11-07
- **Branch:** batch-sections

## Plan

The change: where the CLI reads one JSON object (`Cli.readJson`, for `-` and `@file`), the text
may go on after it in sections. A line `@@name` alone starts one, running to the next such line
or the end, its last newline dropped; each JSON string exactly `"@name"` becomes that text. A
section the JSON does not use, and a name the JSON uses with no section, are refused (the second
is the sign that a heredoc ended early). The same rules as gorefactor's `withSections`.

Derivation: a red test in `CliTest` (a `create_file` whose `content` is a section, read from
stdin; then the two refusals); `add_declaration` of `Cli.withSections` (split, parse, replace)
and `replace_expression` of `readJson`'s parse to call it. Then the usage text, CLAUDE.md's
"Without the MCP server", the README's CLI section and the session notes.

## Notes

- read: gorefactor's `sections.go`, for its exact rules: with no `@@` line the JSON is as it was,
  so a missing section is refused only beside others; the test's third case gives one.
- refusal: `replace_expression` at line 177 found nothing: `add_declaration` had imported four
  names above it, so the line was 181; resent with it. Two calls, not one batch, since this CLI
  had no sections yet to carry the code.
- bulk: `withSections` and `replaceSections` (95 lines), new behavior, written as gorefactor's.
- refusal: the docs batch, the first use of sections, was refused: "section @@size is not used".
  The README's example held a `@@size` line and the heredoc's delimiter, so the shell ended the
  input early and the example's line was read as a section. Nothing changed; the README went
  alone through `new_file=-` and the rest as a batch under another delimiter. A section cannot
  hold a `@@name` line (noted in the session notes).

## Measured

Not measured: a cloud session started outside the checkout has no session log; the notes are the
record.

## Assessment

Through the tools: every change went through `bin/jrefactor` (create_file, add_declaration,
replace_expression, replace_text for the test's string literal and the docs); no hand edits.
The one large write was the new behavior. The plan held, except that the code went as two calls,
not a batch, since the CLI had no sections until this change. The refusals were a moved line
number (import lines added above it) and the heredoc trap, which the new refusal caught before
anything changed.

## Follow-ups

- None for the CLI. gorefactor's `docs/parity.md` can mark code sections done for Java.