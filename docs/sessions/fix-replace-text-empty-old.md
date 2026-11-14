# replace_text: refuse an empty old

- **Item:** issue #462 (code review of 2026-11-13), roadmap #230
- **Date:** 2026-11-13
- **Branch:** fix-replace-text-empty-old

## Plan

One precondition in `Edits.replaceText` beside the two it has: an empty `old` is refused, naming
`append=true` as the way to add at the end. A golden refusal case first.

## Notes

- plan: golden `replace_text_refused_empty_old` first; red as the issue said (OutOfMemoryError in
  `Edits.replaceText`, under `-Xmx256m`).
- refusal: `replace_text` with the JSON as an argument and the `@@` sections on stdin refused
  ("old is not in"): `@old` was taken literally. Resent with the JSON on stdin (`bin/jrefactor -`).
- hand: an empty heredoc for `old_file=-` hit the bug itself (OutOfMemoryError; the CLI restored
  the file).

## Measured

Not measured: the session-log hook is not loaded here. One `replace_text`; no Java by hand.

## Assessment

Through the tools. The CLI's section form cost one resend (friction log).

## Follow-ups

- The CLI could refuse a JSON argument whose strings name `@sections` when none were read.
