# Closing the edit pipeline item as partly done

- **Item:** roadmap order 32 (#39, "Edit pipeline in one place"), partly done.
  The user chose from three options on 2026-11-03: A (finish the gate step
  for multi-file edits), B (close as partly done, with a reason), C (also
  move `Declarations.plan` and `Edits.replaceText` onto `TextEdit`). The user
  answered "go with your recommendation", which was B now, then A only for
  the import-fix duplication.
- **Date:** 2026-11-03
- **Branch:** edit-pipeline-close

## Plan

Close the row, as B says: the single-file edits share `Edits.gated`, the text
application of `Rename`, `MoveType` and `SignatureChange` shares `TextEdit`,
and the two shapes that stay (`Declarations.plan`, `Edits.replaceText`) are
kept on purpose. Record the open part (the per-file import fix and gate) as
the item that follows, in its own PR, under A.

Operations, in order:
1. Change the row's status to "Closed: partly done" and name the open part.
2. Note the closure in `docs/session-notes.md`.
3. Write this record.

The one edit that changes behavior: none. Docs only.

## Notes

- `read:` `docs/design.md` row order 32 and the two existing records
  (`edit-pipeline.md`, `edit-pipeline-text.md`), to check what each says is
  done.
- `hand:` the roadmap row and the session notes were changed with a small
  Python replace (exact-match, asserted once each) because this is a docs
  PR with no Java. No Java was written.
- `plan:` none.

## Measured

Not measured: docs only, no Java edited, no tool calls to java-refactor-mcp
were needed.

## Assessment

- **Through the tools or around them:** no Java changed, so the tools had
  nothing to do. The docs edit was a text replace.
- **Did the plan hold?** Yes: the row and the notes were the only changes.
- **Reading:** the two records and the row, for the status they claim.

## Follow-ups

- Open, under order 32's remainder (to be added to the roadmap in its own PR
  with the code): `SignatureChange` loops over files and calls `Imports.fix`
  then `Gate.applyOrRefuse` for each; `MoveType` does the same for its
  changed files. A shared entry point for a map of file to new text, fixing
  imports per file and gating once, would remove that pair from both.
- Not taken: option C (the two shapes with their own apply).
