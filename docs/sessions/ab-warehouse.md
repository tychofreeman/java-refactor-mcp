# A/B run on a Java task set (roadmap #11)

- **Item:** roadmap #11: an A/B run of the jrefactor tools against a stock
  baseline on a small Java task set. The user's request of 2026-11-04 ("Go
  ahead and run the A/B harness run").
- **Date:** 2026-11-05
- **Branch:** ab-warehouse

## Decisions (this session)

- **gorefactor's harness, not a copy.** `cmd/refactor-ab` (in
  `tychofreeman/go-refactor-mcp`) runs any MCP server given as `-mcp`, any
  task file and any per-arm CLAUDE.md. Two things were Go-specific: it starts
  the server as `<server> -C <dir>`, met by `eval-tasks/jrefactor-ab-server`
  (drops the `-C`); and the mcp arm's Bash allowlist had no Maven, a two-line
  change there (`./mvnw`, `mvn` in `checking`). The server keeps the label
  `gorefactor` in the harness, so the tools show as `mcp__gorefactor__*` and
  the harness's per-tool token tables work unchanged.
- **The task set is a Java port of gorefactor's warehouse traps**
  (`eval-tasks/traps-warehouse.json` there), so the two projects compare on
  the same four tasks: reorder a method's parameters (`apply` passes
  `p.bin(), p.bin()`, whose order of evaluation a naive swap changes), inline
  three helpers (`both(reserve(), charge())` must still charge;
  `within(nextSlot(), ...)` must take one slot), rename a static field to a
  name a nested class's field, a parameter and a local already have, and
  dedupe ten near-identical methods (one compares with `>`). Hidden
  acceptance tests in `eval-tasks/testdata/warehouse-*`, checked with
  `./mvnw -q -o test`. Each hidden test fails on the fixture as given; the
  dedupe test passed on a solution written to check its pattern.
- **Arms:** `mcp` (the tools, Read, Grep, Glob and looking Bash; no Edit or
  Write) against `manual` (Claude Code's own tools), Sonnet, 4 runs each,
  per-arm instructions in `eval-tasks/claude-md/`, adapted from gorefactor's
  `claude-md-derivations`.
- **Runs are not committed** (`/eval-runs/` is ignored, as in gorefactor);
  the summary is below.

## Plan

1. A Maven fixture with the four packages, its tests and the wrapper.
2. The hidden tests, each failing on the fixture.
3. A one-run Haiku smoke test of one task, then the Sonnet run.

## Notes

- `hand:` `chmod +x` of the copied `mvnw` and of the wrapper: `move_file`
  drops the mode, `create_file` writes none (friction log).
- `hand:` the gorefactor allowlist change, by a Python replace (another
  repository; its push was refused by the session's permissions, so the
  change is local, on gorefactor's branch `ab-java-checking`).
- `bulk:` the fixture (about 450 lines of new Java) through `create_file`
  steps in two batches and one CLI call; new code, not moved.
- Smoke run (Haiku, mcp, rename): passed, $0.01. It renamed `Rate.markup`
  first, out of the way, then `margin`.

## Results (Sonnet, 4 runs per arm and task, $3.36 in all)

| task | arm | passed | cost | input tokens | turns | diff lines |
|---|---|---|---|---|---|---|
| reorder | mcp | 4/4 | $0.11 | 263,217 | 13.2 | 14 |
| reorder | manual | 4/4 | $0.11 | 198,553 | 11.8 | 31 |
| inline | mcp | 4/4 | $0.13 | 273,225 | 14.2 | 31 |
| inline | manual | 4/4 | $0.09 | 129,198 | 9.0 | 46 |
| rename | mcp | 4/4 | $0.09 | 164,732 | 10.5 | 26 |
| rename | manual | 4/4 | $0.09 | 153,926 | 9.8 | 46 |
| dedupe | mcp | 4/4 | $0.16 | 246,836 | 11.8 | 49 |
| dedupe | manual | 3/4 | $0.08 | 105,900 | 4.7 | 58 |

- The failed manual run did not start (`exec: "claude": executable file not
  found in $PATH`), a harness fault; 31 of 31 runs that ran passed.
- mcp $1.96 for 16 runs ($0.12 each), manual $1.40 for 15 ($0.09): the tools
  cost 32% more. gorefactor's same task set on Sonnet: +20% (`docs/cost-history.md`
  there).
- Every trap was caught in the mcp arm by a refusal: `move_parameter` refused
  the reordered `p.bin()` calls (4 of 4 runs), `inline_function` the
  reordered, short-circuited and duplicated arguments (all runs), `rename` the
  capture by the local `markup` (1 run; the others renamed around it first).
  Sonnet also avoided every trap by hand in the manual arm.
- Where the mcp arm spent its extra cost (from the transcripts):
  - `extract_variable` refused the extraction the other refusals asked for
    (the first `p.bin()`, `reserve(truck)`): 10 refusals, then
    `replace_statements` by hand in every reorder run. Roadmap #99.
  - Batch steps named by the client's name (`mcp__gorefactor__replace_statements`):
    6 refused batches in 4 runs, each resent. Roadmap #100.
  - Wrong columns for `extract_variable` when an expression occurs twice on a
    line: 3 refusals. Roadmap #101.
  - 37 ToolSearch calls (22 turns doing only that), loading schemas one by one;
    the per-arm CLAUDE.md does not say to load them all at once, as this
    repo's does.

## Measured

From `bin/session-report` (before the docs edits): 37 tool calls, 25 edit
steps through jrefactor (the fixture, by `create_file` and `move_file`), no
refusals; by hand: the `chmod`s, the friction-log append and this record.
Large writes: 10, all the fixture's new code.

## Assessment

- **Through the tools or around them:** through, for this repo's files; the
  gorefactor change was by hand (Go, another repository).
- **Did the plan hold?** Yes.
- **One change that would have helped:** `move_file` keeping a file's mode
  (friction log).
- **The run:** the tools made no run more correct here, because Sonnet avoided
  the traps unaided; they cost a third more, much of it in three mechanisms
  that refused or misled (#99-#101). Rerun after those.

## Follow-ups

- #99-#101, then rerun this set.
- gorefactor's allowlist change (`ab-java-checking` there) needs pushing as a
  PR there; this session's permissions refused the push.
- Per-arm instructions: say to load every jrefactor schema with one ToolSearch.
- Harder tasks (feature work, as gorefactor's `ab-features`), where reading
  cost dominates.
