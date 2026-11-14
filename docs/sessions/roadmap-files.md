# One file per roadmap item

- **Item:** the roadmap table in `docs/design.md` split into one file per item (the user,
  2026-11-14; roadmap #491)
- **Date:** 2026-11-14
- **Branch:** roadmap-files

## Plan

Split the three roadmap tables (Done, Done from the ordered list, Open in order) into
`docs/roadmap/<item>-<slug>.md` with a script, check it by rebuilding each row from its file and
comparing it with the original line, replace the tables with a paragraph, add `bin/roadmap` and
`RoadmapTest`, then update every doc that told a session to add or edit a row.

## Notes

- bulk: 272 roadmap files written by a scratch script (not committed), generated text.
- hand: the five rows that did not split cleanly: a `|` inside code split three of them (items
  266, 486 and the rows without an Evidence cell, 264 and 330), and item 43 had a closing note
  after its last cell, now appended to its Status.
- plan: `status` in the header is derived from the Status text: "Built" (item 132) is Done,
  "Inline class done ...; split deferred" (128) is Partly done, "Split into rows" (112, the
  parity umbrella) is Open. The first ten items done (the first table) had no order and no
  Status: `order: none` and Status "Done".
- `RoadmapTest` written with `create_file` and `replace_declaration`; `.github/workflows/ci.yml`
  with a script (YAML, no tool shape for it).
- CI skipped the build for docs-only changes, so a PR adding only a roadmap file would not have
  run the test: a `roadmap` job runs `RoadmapTest` alone then, and does not release.

## Measured

Not measured (no session-log hook here).

## Assessment

Docs and a script, little Java; the Java went through the tools. The round trip found every row:
267 rebuilt exactly, the five above differ only as described.

## Follow-ups

- gorefactor may want the same; not changed here.
- A clash between two open PRs shows only once both files are in one tree: merge `main` in
  before merging when it gained roadmap files.
