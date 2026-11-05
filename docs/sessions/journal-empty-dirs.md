# The journal leaves no empty directories (friction log, 2026-11-04)

- **Item:** the friction-log entry of 2026-11-04 ("a refused batch leaves the
  directories its create_file steps made"); the user said "continue" after #77.
- **Date:** 2026-11-04
- **Branch:** journal-empty-dirs

## Plan

`Journal.put` is the one writer of project files: when it deletes one (a null
text, on a refusal, an undo, a deletion), it removes the parent directories the
deletion left empty, up to the first that is not. Red: `JournalTest`
`deletingAFileRemovesTheDirectoriesItLeavesEmpty`; green: `replace_declaration`
of `put`.

## Notes

- Red, then green; the goldens, which undo each write case, still pass.
- I wrote `java.util.stream.Stream` in full; `replace_text` made it an import.
- A directory that was empty before the call and is a parent of a file the call
  created and then removed would be removed too. Not seen; noted.

## Measured

All Java through jrefactor (`add_declaration` of the test, `replace_declaration`
of `put`, one `replace_text`), no refusals.

## Assessment

- **Through the tools or around them:** through.
