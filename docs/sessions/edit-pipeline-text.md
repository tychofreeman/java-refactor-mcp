# One text-application record: TextEdit

- **Item:** roadmap order 32 (#39, partly done): text is applied four ways
  (`Rename.apply`, `SignatureChange.apply`, `Edits.replaceText`,
  `Declarations.plan`). Chosen with "Do both" after "What's next?" (2026-11-03;
  the other option, order 44, is the next PR).
- **Date:** 2026-11-03
- **Branch:** edit-pipeline-text

## Plan

One top-level record `TextEdit(start, end, text)` with one `apply`: the edits
go back to front, so each offset stays valid. Three sites use it: `Rename`
(its `apply` becomes a delegate over the occurrences), `MoveType` and
`SignatureChange` (their private `Edit` records and loops are deleted). The
nesting refusal in `SignatureChange` stays where it was, checked before the
apply. Not changed: `Declarations.plan` (its changes carry a cursor and a
description, and apply forward) and `Edits.replaceText` (it tracks the new
line numbers as it writes). Both keep their shape, and the record says why.

## Notes

- `read:` `Rename.apply`, `Edits.replaceText`, `SignatureChange`'s loop,
  `MoveType.apply`, `Declarations.plan` (to see its change shape), once each.
- `plan:` the record was made with `create_file`; then one batch
  (`check: end`) retyped `MoveType` (23 references), `SignatureChange` (6) and
  the `Rename.apply` body, and deleted the two private records. The batch
  needed no refusal and no fix.
- `hand:` none. Every change was a tool step.
- `bulk:` none beyond the batch's own steps.

## Measured

Not measured: no session log for this branch.

## Assessment

Through the tools: the sites were changed by `replace_text`,
`replace_declaration` and `create_file`, in one batch checked at the end. The
goldens (rename, move_type, signature changes, declarations, extract) pass
unchanged, so the refactor's behavior held. The plan held for three of four
sites; the fourth, `Declarations.plan`, has a different shape and was left.
The `SignatureChange` loop was reordered so the nesting check runs before the
apply; both throw the same refusal at the same edit.

## Follow-ups

- `Declarations.plan`'s change shape and `Edits.replaceText` could share a
  record if their results were reshaped; that is a larger change and waits
  for a need.
- The per-file `Imports.fix` loop in `SignatureChange` is untouched.
