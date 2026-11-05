# replace_text with append

- **Item:** the friction-log entry of PR #182 (another session, 2026-11-05): an
  80-line CI job appended by shell redirect, since `replace_text` could not add at
  the end of a file; this session had appended three friction-log entries by heredoc
  for the same reason. The user's "yes" of 2026-11-05.
- **Date:** 2026-11-05
- **Branch:** replace-text-append

## Decisions (this session)

- **An option, gorefactor's:** `append=true` in place of `old` adds `new` at the end
  of the file, as gorefactor's `replace_text` does; an option on an existing tool,
  not a tool (CLAUDE.md). `old` is no longer required by the schema; the tool asks
  for one of the two.
- **A line break first** when the file does not end in one, so an appended line is a
  line of its own (`replace_text_append_no_newline`).
- Refusals in gorefactor's words: `append` with `old` (or `all_occurrences`), and
  neither.
- Built while #182 was open; my PR waits for it, since both append to the friction
  log.

## Notes

- Goldens red first (the unknown argument), then green.
- The friction-log entry of this work was appended with the new option, through
  `bin/jrefactor --rebuild` (the session's server predates it).
- `hand:` none.

## Measured

All Java through jrefactor (`replace_text`, `add_declaration`); goldens by
`create_file`.
