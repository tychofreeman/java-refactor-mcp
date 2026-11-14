---
order: 32
item: 39
status: Closed
---

## Item

Edit pipeline in one place: parse, edit text, `Imports.fix`, `Gate.applyOrRefuse` is written out at six sites (`Declarations` x3, `Edits`, `Extract`, `Signatures`), and text application four ways (`Rename.apply`, `Signatures.Change.apply`, `Edits.replaceText`, `Declarations.plan`)

## Status

Closed: partly done (2026-11-03)

## Evidence

Full code review 2026-11-03. The import fix and the gate are one call (`Edits.gated`) at Declarations (3), Extraction and `Edits.write`; `Signatures` (per-file loop) and the four text-application ways are not done. Record: `docs/sessions/edit-pipeline.md` Text application: `TextEdit` (one record, one `apply`) is used by `Rename`, `MoveType` and `SignatureChange`; `Declarations.plan` and `Edits.replaceText` keep their own shape (see the record). Record: `docs/sessions/edit-pipeline-text.md`. Closed by the user's choice (2026-11-03) as partly done. The per-file import fix and gate in `SignatureChange` and `MoveType` was closed on 2026-11-04 as not worth merging: the two loops fix different things (`docs/sessions/import-fix-close.md`)
