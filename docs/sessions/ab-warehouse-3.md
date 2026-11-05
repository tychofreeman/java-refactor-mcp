# Third A/B run, after #102 and #103

- **Item:** the Java warehouse set again, after `inline_function` binds arguments
  (#102, #180) and `fold` (#103, #181). The user's request of 2026-11-05.
- **Date:** 2026-11-05
- **Branch:** ab-warehouse-3

## Method

As before (`docs/sessions/ab-warehouse.md`): gorefactor's `cmd/refactor-ab` at its
`main`, this repo's `main` at fa24deb (jar built before the run), Sonnet,
`-arms mcp,manual -runs 4`. One change to the mcp arm's instructions, with `fold`:
merge near-duplicates by writing the shared method and folding it
(`eval-tasks/claude-md/mcp.md`). Output in `eval-runs/warehouse-sonnet-3` (not
committed). $3.00.

## Results: three runs, cost per run (passed, turns)

| task | arm | run 1 | run 2 (#99-#101) | run 3 (#102, #103) |
|---|---|---|---|---|
| reorder | mcp | $0.108 (4/4, 13.2) | $0.093 (4/4, 10.5) | $0.099 (4/4, 12.0) |
| reorder | manual | $0.107 (4/4, 11.8) | $0.105 (4/4, 11.5) | $0.105 (4/4, 13.0) |
| inline | mcp | $0.134 (4/4, 14.2) | $0.142 (4/4, 17.8) | $0.077 (4/4, 7.0) |
| inline | manual | $0.088 (4/4, 9.0) | $0.090 (4/4, 10.5) | $0.086 (4/4, 9.2) |
| rename | mcp | $0.090 (4/4, 10.5) | $0.092 (4/4, 11.2) | $0.090 (4/4, 10.2) |
| rename | manual | $0.093 (4/4, 9.8) | $0.094 (4/4, 10.0) | $0.095 (4/4, 10.0) |
| dedupe | mcp | $0.157 (4/4, 11.8) | $0.149 (4/4, 14.2) | $0.112 (3/4, 10.0) |
| dedupe | manual | $0.082 (3/3, 4.7) | $0.093 (4/4, 6.2) | $0.085 (4/4, 4.8) |

- Per run over all tasks: mcp $0.122, $0.119, $0.095; manual $0.093, $0.096, $0.093.
  The tools' extra cost: +31%, +24%, **+2%**.
- **Inline:** every mcp run was three `inline_function` calls (one batch in two
  runs) and no refusals, against 20 refusals in the second run. Cheaper than manual.
- **Dedupe:** every mcp run was `add_declaration` of a helper and one `fold` (two
  then one `replace_statements`); $0.15 to $0.11, still above manual's $0.085.
- **The first real failure of the three runs:** dedupe mcp run 2 folded only the
  shared statement (`alerts.add(String.format(...))`) into `report`, keeping each
  probe's `if (v >= limit)` so that `Gas` keeps its `>`. Behavior is the same (the
  at-limit test passed); the task's "make them call one static method" is not
  met: the probes are not one call. A shallower merge than asked, which the tools
  made easy; not a tool fault.
- Reorder and rename: as before, at or below manual.

## Assessment

With #99-#103 the tools arm costs what the manual arm does on this set, and is
cheaper on three of four tasks. The noise between runs is about 10%, and this is
one set of four small trap tasks, so it says the gap closed here, not that the
tools save cost in general. Every trap was still handled safely in both arms.

## Follow-ups

- Dedupe: the condition that differs (`>=`, `>`) can be a parameter of the helper
  (`boolean over`), which `fold` matches as any expression; saying so in the
  instructions is guidance, not a mechanism, and would need measuring.
- A larger task set (gorefactor's `ab-features`), where reading cost dominates.
