# Option gaps against gorefactor

- **Item:** issue #371, roadmap #204: `replace_text` `files` and `function`, `replace_declaration`
  `line` and `column`
- **Date:** 2026-11-15
- **Branch:** option-gaps

## Plan

Three options on existing tools, each the smallest change: `files` a loop over the one-file edit
in one call (the journal makes it one undo); `function` a range the search stays within;
`line` a translation to the declaring file and `Type.member` before `Declarations.replace`, as
`ByLine.named` does for the signature tools.

## Notes

- read: gorefactor's `internal/edits/replacetext/replacetext.go` and `replacedecl.go` arguments.
- plan: eight goldens first; red (three failures, four errors).
- bulk: batches of 5, 2, 2 and 2 steps (`replace_text`, `add_declaration`).
- plan: refused `append` with `files` at first, then took it back to match gorefactor.
- hand: one golden's expected output by `sed` (a resource); no Java.

## Measured

Not measured (no session-log hook). Java changes: 5 tool calls, none by hand.

## Assessment

Through the tools.

## Follow-ups

- None.
