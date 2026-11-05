# move_file keeps a file's mode, and undo restores it

- **Item:** the friction-log entry of 2026-11-05 ("move_file drops the executable
  bit"), from copying `mvnw` into the A/B fixture. The user's request of 2026-11-05.
- **Date:** 2026-11-05
- **Branch:** move-file-mode

## Decisions (this session)

- The journal holds text, so a file it creates gets the default mode: a copy, a
  move's destination, and a file undo puts back after a move or delete. A file it
  overwrites keeps its mode already.
- `MoveFile` gives each destination its source's mode (`Journal.copyMode`). The
  journal records the mode a file had when a call first writes it (`Journal.mode`,
  read in `Gate.write` before the write, since a deletion leaves nothing to read),
  and `restore` puts it back on undo and abort; the command line's saved history
  keeps it (`mode` per file in the JSON). A file system without modes is skipped.
- `restore(texts)` stays for the gate's callers, as `restore(texts, Map.of())`.

## Notes

- Three tests in `JournalTest` red first (`rw-rw-r--` for `rwxr-xr-x`): a move and
  its undo, a copy, and a deletion undone from the saved history.
- `refusal:` the first batch replaced the one-argument `restore` the gate uses and
  reused the name `to`; resent keeping both.
- `hand:` none.

## Measured

All Java through jrefactor: one batch of eleven steps (`replace_text`,
`replace_declaration`, `add_declaration`); the tests by `add_declaration`.
