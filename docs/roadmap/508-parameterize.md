---
order: 498
item: 508
status: Done
---

## Item

`parameterize`, gorefactor's tool by its name and arguments: methods of one type that are the same
but for constants become calls of one new method that takes those constants as parameters (issue
#372).

## Status

Done 2026-11-15: `Parameterize`, published (the owner's option (B), over an option on `fold` and
over an unpublished alias only; defaults in `docs/design.md`). Goldens `parameterize_constants`,
`parameterize_methods` and four refusals; probe `parameterize_values`.

## Evidence

Issue #372 and the parity review of 2026-11-13: a recipe or a batch written for gorefactor that
calls `parameterize` was refused here, and the operation it names had no tool.
