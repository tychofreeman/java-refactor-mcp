# Split Extract: the extraction in its own file

- **Item:** roadmap order 25 (review row 32): split large classes when each
  is next changed. The user's "Do #25" was read as this item (the summary
  of the session; the `#` column reads pull up and push down for 25). The
  item's rule is "no split without a change that needs it"; the user's
  order asked for it, so this record says it was done on request.
- **Date:** 2026-11-03
- **Branch:** split-extract

## Plan

One class moves: `Extract`'s nested `Extraction` (about 660 lines, the
planning and edit of one extraction) becomes its own file. Its helpers
(`typeName`, `and`, `ancestor`, `endOf`, `lineEndOffset`, the `Selection`
record) stay in `Extract` and become package-private, so the moved class
reaches them as `Extract.x`. Nothing else changes; the result text and
behaviour are the same.

Operations, in order:
1. Find the nested class's lines and the helpers it uses (outline, no body
   read beyond that).
2. Move the class to `Extraction.java` (top level, same package).
3. Widen the five helpers from private to package-private; qualify the
   moved class's calls to them.
4. Let the formatter fix indentation and imports.

## Notes

- `hand:` the move was a script: the class's 660 lines were copied from
  `Extract.java` to `Extraction.java`, deleted from `Extract.java`, and the
  helper references were qualified with a regular expression. No
  java-refactor-mcp tool moves a type or member to a new file; a
  `create_file` would have needed the 660 lines typed out, and a
  `replace_declaration` with empty content only deletes. Logged in the
  friction log.
- `bulk:` 660 lines moved by script. Existing code moved, not new behavior.
  The operation that would have moved it: a `move_type` (or
  `extract_class`) tool, which does not exist. Named in the friction log.
- `plan:` the plan named only the move and the widening; the qualifiers
  were applied to the names found by grep before the move, and the compiler
  found no others.
- `refusal:` none.
- `read:` the outline of `Extract` (declarations and their line numbers)
  and the helper references in the moved range. No body read for the change.

## Measured

Not measured. No session log for this branch
(`.claude/session-log/split-extract.jsonl` does not exist). Line counts:
`Extract.java` 1,045 to 346; `Extraction.java` 713 (new).

## Assessment

- **Through the tools or around them:** around, once. The move was a script,
  and no tool moves a type to its own file. The gap is named in the friction
  log.
- **Large writes:** one, the 660-line move. Existing code, moved; the
  operation that would have done it does not exist yet.
- **Did the plan hold?** Yes. The compiler found no helper it had missed.
- **Reading:** outline only.
- **One change that would have helped:** a `move_type` (move a nested class
  or a member to a new file in the same package, fixing references and
  imports). Recorded in the friction log.

## Follow-ups

- `Extraction` (713 lines) is still large; the rest of the split list
  (`Signatures` 815, `Declarations`, `Tools`) waits for a change that needs
  it, as the roadmap says.
