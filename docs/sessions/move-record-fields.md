# `move` of a member record whose fields the outer type reads

- **Item:** issue #440 (the Java 17 stress run, `docs/sessions/stress-enum-switch.md`), roadmap #192
- **Date:** 2026-11-13
- **Branch:** move-record-fields

## Plan

A read of a record's field across the new boundary becomes a call of its accessor, which means
the same when the accessor is generated or only returns the field. Derivation: in
`MoveType.qualifyAndWiden`, where a private member used across the boundary is widened, a
record's field cannot be widened; insert `()` after the read's name instead, and refuse, naming
the field and the accessor, when the accessor does more. Golden cases first: the read from the
outer type, the read from the moved type of a record left behind, the refusal; a probe.

## Notes

- read: `MoveType` through `inspect` (outline, `run`, `qualifyAndWiden`, helpers); `NameCheck` lines 25-229.
- plan: the goldens did not reach the gate: `NameCheck` refused first, twice, with other wrong
  reasons. A record's field has no declaring node in JDT (`findDeclaringNode` is null), so the
  moved record's own accessor `return length;` looked captured; and `package p;` was matched with
  a local `p` of the moved code. Both fixed here (`NameCheck.declaration`, `IPackageBinding` skipped).
- bulk: two `batch` calls (six and four steps) wrote all of the Java; formatting by Spotless.
- hand: golden and probe files (resources, plain files); the docs.
- read: a single golden case cannot be run alone (`GoldenTest` runs every case); a scratch script
  ran each case through the CLI on a copy of `in/` and diffed it with `out/`.

## Measured

Not measured: the session-log hook is not loaded in this session. Two tool calls carried every
Java change.

## Assessment

Through the tools: both batches applied first time. The plan held, plus the two `NameCheck`
holes it uncovered, which the issue's repro (no explicit accessor, package `p` with no local `p`)
did not show.

## Follow-ups

- The gate's fix line for `move` ("Rename one of the clashing types") fitted only a clash: replaced in #441's branch, `move-sealed-permits`.
