# MoveType through the gate

- **Item:** the follow-up of `docs/sessions/rename-on-gate.md`: `MoveType`
  still said "would introduce compile errors" and had no fix line, where
  `Rename` and the other edits say "would add" and name a fix. Chosen from
  "What's next?" on 2026-11-03.
- **Date:** 2026-11-03
- **Branch:** movetype-gate

## Plan

`MoveType` calls `Gate.applyOrRefuse` with its own "moving Outer.Inner to
file" wording and a fix line, and passes the stubs its gate declared into
its result. Operations: a golden that makes the move clash (the moved type's
file already declares a top-level type with the same name in another file of
the package), red with the expected "would add"; then the one call.

## Notes

- `read:` `MoveType` around its gate (about lines 170-215), to see the one
  refusal and the result's use of the gate's stubs.
- `plan:` the golden needed a trigger. Three candidates first: a private
  constructor the outer class calls, and a private nested interface the moved
  class implements. Both moved cleanly (the tool widens them), so neither
  refuses. The duplicate top-level type did refuse, and is the golden.
- `hand:` none in the code. Golden files: `create_file` and `replace_text`.
  Two earlier candidate runs left `create_file` refusals (the files already
  existed) unnoticed; the case was rebuilt from scratch.
- `bulk:` none.

## Measured

Not measured: no session log for this branch.

## Assessment

Through the tools: the change was one batch of two `replace_text`, checked at
the end; the golden went red on the wording before the change. The search for
a refusal trigger took three goldens, which is the cost of a path the tool
rarely hits. A `create_file` refusal that I suppressed hid a stale case for a
moment; the case was rebuilt, which is the lesson for the next golden: check
the output of each `create_file`.

## Follow-ups

- None from this item. `Stubs.typeName` / `Extract.typeName` and the open
  `Stubs` question are unchanged (see the records of orders 36 and 42).
