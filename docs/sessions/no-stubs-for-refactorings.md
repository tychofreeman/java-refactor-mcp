# No stubs for a refactoring's own output

- **Item:** the friction-log entry "stubs hid a wrong move_type"
  (`docs/sessions/move-type-inherited.md`): a refactoring that left a name
  unresolved got an empty stub class for it, and the gate passed the
  change. The user said "go ahead with the next step" (2026-11-03) after it
  was named as the next one.
- **Date:** 2026-11-03
- **Branch:** no-stubs-for-refactorings

## Plan

The change, at the highest level: stubs are for code the model wrote; a
refactoring changes no behavior, so what its output leaves unresolved is
its bug, and the gate refuses it with the errors.

Derivation, in order:

1. Red: `ToolsCallTest`: a tool of `Kind.REFACTORING` whose output calls
   `Cart.total()`, which nothing declares, is refused and `Cart` is left
   alone; the same tool as `Kind.EDIT` gets the stub.
2. Green: `Session.editing`, set by `Tools.call` from the tool's kind; a
   batch keeps it once a step was an edit, for its check at the end;
   `Stubs.declare` declares nothing when it is false.
3. The contract and session notes.

## Notes

- bulk: the test (46 lines), new; red as planned (no refusal).
- plan: steps 1-3 held; no golden case or other test relied on a
  refactoring getting a stub.

## Measured

`bin/session-report` (`.claude/session-log/no-stubs-for-refactorings.jsonl`):

- tool calls: 12
- through jrefactor: 0 refactoring steps, 4 edit steps
- refused by jrefactor: 1
- by hand: 0
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 1
  - add_declaration src/test/java/io/github/tychofreeman/jrefactor/ToolsCallTest.java: 46 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: python3 - <<'EOF'
- reads: Bash java 3

## Assessment

- **Through the tools or around them:** all Java through java-refactor-mcp;
  by hand: the docs and this record.
- **Did the plan hold?** Yes.
- **One change that would have helped:** none.

## Follow-ups

- A batch that mixes edits and refactorings and is checked at the end still
  declares stubs for both, since the check cannot tell whose code a name is
  in.
