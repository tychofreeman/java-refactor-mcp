---
order: 481
item: 491
status: Done
---

## Item

One file per roadmap item: the table in `docs/design.md`, which every PR appended a row to, conflicted on every merge while several agents worked at once, and its numbers were taken twice

## Status

Done 2026-11-14: `docs/roadmap/<item>-<slug>.md` (a header with `order`, `item` and `status`, then the row's Item, Status and Evidence), split from the 272 rows by a script and checked by rebuilding each row; `bin/roadmap` (`--open`, `next`) prints the table; `RoadmapTest` fails on a number two files share or a malformed header, and CI's `roadmap` job runs it when only `docs/roadmap/` changed. The user's choice, over fewer agents at once and over keeping the table (Decisions)

## Evidence

Conflicts on every merge of `main` (some PRs merged it three or four times); two resolutions deleted or brought back a row (#496's, #573's); numbers 175-177 and others taken twice; `docs/sessions/roadmap-files.md`
