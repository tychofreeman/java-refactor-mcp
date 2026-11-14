---
order: 44
item: 51
status: Done
---

## Item

Performance: `Signatures.Family.of` parsed every file per call; it now parses only the files that contain the method's name (`add_parameter` 6.5 s to 2.9 s, Commons Math, one run each). `Imports.toImport` and the stub rounds measured 2026-11-03: an import adds about 0.7 s to a 0.27 s call, a stub round 0.2 s and one more parse. Undo measured 2026-11-04 at under 1 ms: the 2.7-2.9 s first recorded was the harness's own uncached check, now timed apart (`docs/sessions/measure-undo.md`)

## Status

Done

## Evidence

Full code review 2026-11-03. Records: `docs/sessions/reuse-gate-parse.md`, `docs/sessions/measure-imports-stubs.md` (its undo figures are wrong; see `measure-undo.md`). Reusing the gate's parse across calls was not taken: it keeps every AST between calls
