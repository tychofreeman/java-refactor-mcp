# inspect with no arguments: the project's overview

- **Item:** roadmap #111 (friction triage: a probe guessed Maven paths, 2026-11-02), a server
  gap in gorefactor's `docs/parity.md` section 2 (Go's `inspect` lists its module).
- **Date:** 2026-11-06
- **Branch:** inspect-overview

## Plan

`inspect` with no arguments lists the source roots, and under each its packages with their
files' line counts, then the totals, as gorefactor's `inspect.Module`; when the classpath
failed, it says why; it ends naming the other ways to read. Today the call is refused for
want of `file`.

Derivation: golden first; `Inspect.overview(project)` by `add_declaration`; a branch in
`Tools.inspect` (`insert_statements`); the description.

## Notes

- read: `Tools.inspect`, `SourceProject`'s outline; gorefactor's `inspect.Module` (the format).
- One batch (the method and the branch); no refusals. Tried on this repo with
  `bin/jrefactor --rebuild inspect`: 2 roots, 100 files.

## Measured

Through jrefactor: 2 batches (code, docs); no refusals; by hand the golden (resources) and this
record.

## Assessment

Through the tools; as planned. A package with many files is one long line, as Go's.

## Follow-ups

None.
