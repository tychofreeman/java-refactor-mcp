---
order: 489
item: 499
status: Done
---

## Item

Deleting methods that only call each other (`Signatures.pure` and `allPure`) with
`replace_declaration` and empty content was refused for whichever went first, even inside one
`batch`: the safe delete counted the use inside the other method, which the next step deletes.

## Status

Done 2026-11-14: inside a `batch`, a use inside a declaration a later step deletes does not count
(`Batch.deletes` sets `Session.deletedLater` around each step; `SafeDelete.deletedLater` drops the
uses in those ranges). With check `"end"`, members that only call each other are deleted in one
batch in any order; one also used from outside is refused at its step, naming only the outside
use. Under `"each"` the type check still refuses the first delete, with the batch's hint toward
`"end"`. A delete outside a batch is unchanged. The owner's choice of a batch-aware check, over a
list of names on `replace_declaration` and a cascade flag (Decisions in `docs/design.md`).

## Evidence

Issue #623; friction log 2026-11-13 (fix-parameter-argument-exceptions: done as one
`replace_text`). Goldens `batch_delete_cycle`, `batch_delete_cycle_reversed`,
`batch_delete_cycle_three` (across two files), `batch_delete_cycle_refused_outside`,
`batch_delete_cycle_refused_each`; `replace_declaration_delete_refused_used` unchanged.
