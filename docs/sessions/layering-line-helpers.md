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

- `plan:` the premise was stale. `Tools.lines` and `Edits.lineOf` are gone
  (#27 left only `Lines`), and grep found no caller of either. The coupling that
  remains: domain classes return `Tools.Outcome`, and `Declarations` and
  `Extraction` call `Edits.gated`, `Edits.parses` and `Edits.Written`.
- `decision:` the user chose "Outcome out of Tools" over moving the gate helpers
  too, and over closing the item as stale. The gate helpers stay in `Edits`.

## Measured

Not measured: no session log for this branch (`bin/session-report` found none).
Counted from the notes: 1 `move_type` call through `bin/jrefactor` (34 references
in 11 files), 0 hand writes of Java, 1 `spotless:apply` run, 0 refusals.

## Assessment

- **Through the tools or around them:** through the tools. The move was one
  `move_type` call; every reference followed.
- **Large writes:** none.
- **Did the plan hold?** No. The premise (`Tools.lines`, `Edits.lineOf`) was
  stale, and the change became the one the user chose from the real coupling.
  The first two operations of the plan were the check that found this.
- **Reading:** none beyond grep for the callers.
- **One change that would have helped:** the roadmap row should have been
  checked against the code before the item was offered; a row that names
  removed code is an open item that is already done.

## Follow-ups

- `Edits.gated`, `Edits.parses` and `Edits.Written` are still called by domain
  classes (`Declarations`, `Extraction`). The user chose to keep them in
  `Edits`; a later item may move them if the coupling is judged to matter.
