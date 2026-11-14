---
order: 393
item: 403
status: Done
---

## Item

Small helpers were copied across the tree (issue #511, code review): a qualified name's simple name (four private copies, about 15 inline), a unit's package (`MoveType.packageOf`, five inline), plurals (`MoveType.plural`, `SessionReport.plural` beside `Names.plural`), "a, b and c" (`MoveType.joinAnd`, `Rename.joinAnd`, `Batch.listed`, `Extract.and`, two inline), seven `new ObjectMapper()`, four SHA-256 digests, a read-or-null (`Journal.read` private, four copies) and a problem-to-node lookup (two)

## Status

Done 2026-11-14: `Names.simpleName`, `Names.packageOf`, `Names.listed` (moved by `move`), `Json.MAPPER`, `Hashes.sha256Hex`, `Journal.read` (package-visible), `Nodes.at(unit, problem)`; `fold` rewrote the inline copies (18, 5, 2, 4 and 2 sites) and `inline` removed the private ones. The SHA-256 keys keep their lengths (`Cli` 16 hex digits, `Headers` 32), so caches stay valid. No behavior change: goldens and probes are the check, `NamesTest` and `HashesTest` pin the helpers. Left: about 40 inline `n == 1 ? "" : "s"` inside longer sentences, sites `fold` left (a call evaluated twice), `MethodFamily.simpleName` (strips a parameter list too), `Batch`'s read (a directory reads as null)

## Evidence

Issue #511; `docs/sessions/small-helpers.md`
