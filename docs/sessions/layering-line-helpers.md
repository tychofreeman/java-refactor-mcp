# Domain classes call the line helper, not the tool registry

- **Item:** roadmap #39 (order 46), from the full review of 2026-11-03: domain
  classes import `Tools.lines` and call `Edits.lineOf`, so they depend on the MCP
  registry. Chosen by the user from `what's next?`, 2026-11-04, as the third of the
  remaining candidates.
- **Date:** 2026-11-04
- **Branch:** layering-line-helpers

## Plan

The change, at the highest level: the domain classes depend on `Lines`, the
line helper class that #27 made, and nothing depends on the registry (`Tools`)
or on `Edits` for a line number.

Operations, in order:

1. Find every caller of `Tools.lines` and `Edits.lineOf`: the uses the change
   must reach.
2. Redirect each caller to the `Lines` helper that does the same thing, with
   the tool that fits (`replace_text` for a call site, `rename` only if a
   helper's name must change). No behaviour change.
3. Delete `Tools.lines` and `Edits.lineOf` when nothing calls them
   (`replace_declaration` with empty content). If one of them is not a line
   helper at all, it stays, and the record says why.
4. The check is the existing tests and `./mvnw verify`: a refactoring with no
   behaviour change needs no new golden.

Hypothesis, to confirm in step 1: after #27, `Tools.lines` and `Edits.lineOf`
are the last two copies, each a wrapper over `Lines` or its own duplicate.

## Notes

