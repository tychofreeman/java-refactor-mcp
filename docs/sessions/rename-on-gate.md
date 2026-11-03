# Rename onto the gate

- **Item:** roadmap order 33 (#40): `Rename.gate` re-implemented `Gate.applyOrRefuse`
  and said "would introduce" where every other edit says "would add". Chosen
  from the "What's next?" options of 2026-11-03 (the user answered "Do all three
  in order": this is the first of three).
- **Date:** 2026-11-03
- **Branch:** rename-on-gate

## Plan

Make the rename's write go through the one refusal wording: `Rename.gate`
calls `Gate.applyOrRefuse` with its own "renaming 'a' to 'b'" and its
clash fix. Operations: change the golden's expected wording first (red), then
replace the body of `Rename.gate` with the call (green). No renaming of the
four callers. The refusal gains the Maven classpath note that `applyOrRefuse`
adds when an error reads "cannot be resolved".

## Notes

- `plan:` the wording change is the contract change: the golden
  `rename_refused_compile/refused.txt` changed in the same commit as the code.
- `hand:` none. The golden fragment and `Rename.gate` were changed with
  `bin/jrefactor replace_text` and `replace_declaration`.

## Measured

Not measured: no session log for this branch (the hook is loaded only by a
session started after `.claude/settings.json` existed).

## Assessment

Through the tools: both edits were made by `bin/jrefactor`, the CLI, since
the MCP server was not in this session's tool list. The red step was a
`replace_text` on the golden, and the failing case was the one the change
targets (`GoldenTest` case 68). The only new behavior is the classpath note on
unresolved names, which `Gate.applyOrRefuse` already gave every other edit.
`MoveType` still says "would introduce" (see follow-ups).

## Follow-ups

- `MoveType.move` still says "would introduce compile errors" and has no fix
  line; it should use `Gate.applyOrRefuse` too, with its own golden.
