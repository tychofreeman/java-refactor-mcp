# The gated edit in one place

- **Item:** roadmap order 32 (review row 39): "parse, edit text, `Imports.fix`,
  `Gate.applyOrRefuse`" is written out at six sites. Chosen by the user on
  2026-11-03 from the next-item options.
- **Date:** 2026-11-03
- **Branch:** edit-pipeline

## Plan

One helper does the pair every single-file edit repeats: `Imports.fix` for the
new text, then `Gate.applyOrRefuse` on a one-file map. `Edits.write` already
does this for `create_file`, `replace_text` and `add_declaration`; the helper
is that pair, moved out of `write`'s non-Java branch and its syntax check, so
the same method serves `Declarations` and `Extraction`.

Operations, in order:
1. `Edits.gated(project, path, before, after, chosen, what, fix)`, returning
   the `Written` record that `write` already builds.
2. `Edits.write` calls `gated` after its non-Java branch and syntax check.
3. `Declarations` (three sites) and `Extraction` (one) call `gated`.
4. Left as they are: `Signatures`, which fixes imports per file across many
   files and gates one map; and the text-application four ways (order 32's
   second half), which is a separate item.

The one edit that changes behavior: none intended. `Declarations` and
`Extraction` do no syntax check before the gate today, so `gated` does not
add one.

## Notes

- `read:` the six gate sites (`Declarations` 57, 170, 201; `Edits.write`;
  `Extraction` 223; `Signatures` 606) to find the shared pair.
- `plan:` `Signatures` is left out of the helper: its loop fixes imports per
  file, so forcing it through one map would change its code more than it saves.

## Measured

Not measured. No session log for this branch
(`.claude/session-log/edit-pipeline.jsonl` does not exist). Verify: 142 tests,
0 failures, 2 opt-in cost tests skipped. The golden cases for the four
converted sites (`add_declaration`, `replace_declaration`, delete, and
`extract_method`) guard the behaviour.

## Notes (continued)

- `hand:` `Edits.java`, `Declarations.java` and `Extraction.java` changed with
  the Edit tool. The java-refactor-mcp tools were not in this session's tool
  list. `replace_text` would have made each edit. Logged in the friction log.
- `bulk:` none. The new helper is about 15 lines, and the converted sites are
  each a four-line swap.

## Assessment

- **Through the tools or around them:** around. Every edit was by hand with
  Edit, because no java-refactor-mcp tool was in the session. A `replace_text`
  batch with check "end" would have made the six-file change in one call.
- **Did the plan hold?** Mostly. Four sites converted, as planned. `Signatures`
  was left out on purpose (its per-file loop). The text-application four ways
  is not touched; it is the second half of order 32.
- **Behaviour:** unchanged; the refusal wording passed in to `gated` is the
  same text as before at each site.
- **One change that would have helped:** the java-refactor-mcp tools in the
  session, so the edits went through `replace_text`.

## Follow-ups

- `Signatures.Change.apply` and `Rename.apply` still apply text in their own
  way (order 32's second half); `Signatures` still fixes imports in its own
  per-file loop.
