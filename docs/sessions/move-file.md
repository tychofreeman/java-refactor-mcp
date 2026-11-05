# move_file (roadmap #81)

- **Item:** roadmap #81: move or copy a file that is not Java (a fixture, a
  doc, a golden case directory), as gorefactor's `move_file`, and delete a
  directory. Friction log: the `move_file` idea, "no tool moves golden
  directories", `rm` of directories by hand.
- **Date:** 2026-11-04
- **Branch:** move-file

## Decisions (this session)

- **One tool, gorefactor's name:** `move_file` with `file`, `to`, `copy`, and
  `delete` (no `to`) for removing a file or directory. Not chosen: a second
  tool for deletion (the tool count; deleting is moving to nowhere).
- **Java under a source root is refused**, file or inside a directory: its
  package would break; `move_type` (with `package`) moves types.
- **Text files only:** each file goes through `Gate.write` and the journal
  as text, so `undo` restores it; a file that is not UTF-8 text is refused
  (use `git mv`). Directories left empty by a move or delete are removed.

## Plan

1. Red: goldens `move_file_simple`, `move_file_directory`, `move_file_copy`,
   `move_file_delete`, `move_file_refused_java`, `move_file_refused_exists`,
   by `create_file`.
2. Green: `create_file` of `MoveFile`; in `Tools`, `MOVE_FILE` (by
   `add_declaration`) and its place in `ALL` (`replace_text`).

## Notes

- `read:` `Gate.write`, `Journal.put` and `Edits.write`, to find how a
  non-Java file is written and deleted through the journal.
- The six goldens were red as an unknown tool, not run before the code;
  all passed on the first run after it.

## Measured

From `bin/session-report` (before the docs edits):

- tool calls: 11
- through jrefactor: 31 edit steps (28 `create_file` for the goldens and
  `MoveFile`, `add_declaration` of `MOVE_FILE`, a `replace_text` in `ALL`)
- refused by jrefactor: 0
- by hand: 1 (the record), then the docs by Python and `sed`
- large writes: 2 (`MoveFile` and `MOVE_FILE`, new)

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes.
- **One change that would have helped:** none.

## Follow-ups

- Binary files (images, jars) are refused; the journal holds text.
- Moving Java that is outside a source root (a golden case's `in/`) is
  allowed, since nothing compiles it.
