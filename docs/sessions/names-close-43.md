# Closing the names item (order 43)

- **Item:** roadmap order 43 (#36, Names), still marked Partly done. Chosen by
  the user from "What's next?" on 2026-11-04 (the other options: the A/B run,
  which costs money, and splitting `Declarations` or `Tools`, which no change
  needs yet).
- **Date:** 2026-11-04
- **Branch:** names-close-43

## Plan

Close the row, since each of its three parts is done. Docs only; no Java.

Operations, in order:
1. Check each part of the row against the code: `Stubs.label` gone,
   `Rename.typeLabel` gone, `Signatures.label` on `Names.typeLabel`, and the
   wildcard decision in `docs/sessions/stubs-keep-wildcards.md`.
2. Change the row's status to Done, with the evidence.
3. Write this record.

The one edit that changes behaviour: none. Docs only.

## Notes

- `read:` `Signatures.label` (lines 362-375), to confirm it uses `Names.typeLabel`.
  The other checks were `grep` for the names, not reads.
- `hand:` the row was changed with a Python exact-match replace, since this is
  a docs-only change with no Java.
- `plan:` none.

## Measured

Not measured: docs only; no session log for this branch.

## Assessment

- **Through the tools or around them:** no Java changed, so the tools had
  nothing to do.
- **Did the plan hold?** Yes. The three parts were checked before the row changed.

## Follow-ups

- None for this row. The open part of the wider names question, if any, would
  need a new friction entry.
