# Closing the import-fix remainder of order 39 as not worth merging

- **Item:** the open part of roadmap order 39 (the per-file import fix and gate in
  `SignatureChange` and `MoveType`), left open by `docs/sessions/edit-pipeline-close.md`.
  The user chose it from "What's next?" on 2026-11-04, then asked for the issue to be
  explained and chose to close it.
- **Date:** 2026-11-04
- **Branch:** import-fix-close

## Plan

Close the remainder as not worth merging. Docs only; no Java.

Operations, in order:
1. Record why: the two loops fix different things. `SignatureChange` fixes every
   changed file with the same imports and collects notes. `MoveType` fixes only files
   in another package, with a per-file import and no notes. A shared helper would save
   about five lines per caller and would force a decision on files outside the source
   roots, which `Edits.gated` skips and `SignatureChange` does not.
2. Change the order 39 row's note to say the remainder is closed, not open.
3. Update the session note that names the open part.

The one edit that changes behaviour: none. Docs only.

## Notes

- `read:` `SignatureChange`'s file loop and `MoveType`'s per-file fix, to compare them.
  Read once, in the earlier turn.
- `hand:` the row and notes were changed with a Python exact-match replace, since this
  is a docs-only change with no Java.
- `plan:` none.

## Measured

Not measured: docs only; no session log for this branch.

## Assessment

- **Through the tools or around them:** no Java changed, so the tools had nothing to do.
- **Did the plan hold?** Yes; the decision was option B, which the user chose.

## Follow-ups

- None for this item. If a third caller of the import-fix-and-gate pair appears, that
  is the evidence to reopen it.
