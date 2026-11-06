# A batch's import for a name a later step declares

- **Item:** roadmap #108, a parity bug (gorefactor's `docs/parity.md` section 1); friction log
  2026-11-03: step 1 used `Query`, step 4 added the nested record `Query`, and step 1's import
  fix had imported `javax.management.Query`, which stayed.
- **Date:** 2026-11-06
- **Branch:** batch-later-import

## Plan

An import that the running call or batch added (not in the file's text before it,
`Journal.written`) and that a type declared in the file now shadows is removed by the next import
fix: the later step's. Imports the file had before are left as they were.

Derivation: golden first (a batch, check "end", of a use then the member record); then one
condition in `Imports.fix`'s removal loop (`replace_statements`).

## Notes

- read: `Imports.fix`'s removal loop.
- Red as the friction entry; green with the one condition; `verify` passed first time.

## Measured

Through jrefactor: one `replace_statements` and the docs batch; no refusals; by hand the
golden's fixtures (resources) and this record.

## Assessment

Through the tools; as planned. The rule works in every check mode, since each step fixes its own
imports; only imports the batch added are touched.

## Follow-ups

None.
