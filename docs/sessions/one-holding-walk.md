# One walk over the files that hold a name

- **Item:** roadmap #59 (local session's list, 2026-11-03): `Rename`'s
  field, method and type renames and `MoveType` each read the files whose
  text holds the name and parse them together, written out four times.
- **Date:** 2026-11-04
- **Branch:** one-holding-walk

## Plan

The change, at the highest level: "the files that hold this name, read and
parsed together" is one value, `Holding`, that the renames and the move
ask for. No behavior changes; the golden cases hold it.

1. `create_file` `Holding`: a record of texts and units, with `of(project,
   name, also)` (`also`: a file to include whatever it holds, the declaring
   file for `move_type`).
2. `replace_text` at the four sites; `replace_declaration` deletes
   `Rename.holding`.

`MethodFamily` keeps its own: it reads every file, for its lambda search.

## Notes

- plan: one batch of five steps, as planned; `verify` passed on the first
  run. Net 15 lines fewer.

## Measured

- tool calls: 4
- through jrefactor: 0 refactoring steps, 5 edit steps
- refused by jrefactor: 0
- by hand: 1
  - Bash: git checkout -q -b one-holding-walk && cat > docs/sessions/one-holding-walk.md <<'EOF'
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 1
  - create_file src/main/java/io/github/tychofreeman/jrefactor/Holding.java: 28 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: python3 - <<'EOF'

## Assessment

- **Through the tools or around them:** all through java-refactor-mcp.
- **Large writes:** `Holding` (28 lines), new; nothing typed out again.

## Follow-ups

- None.
