# The shop feature tasks, ported, and the A/B run

- **Item:** the user, 2026-11-05: port gorefactor's feature tasks and run the A/B, to test
  the constructive-transformations thesis on feature work (`docs/design.md`).
- **Date:** 2026-11-05
- **Branch:** ab-shop-tasks

## Plan

gorefactor's `ab-features` runs used features of its own Go source, which do not port;
its `design-*` tasks on the seeded `testdata/shop` module do. Port the module to a Maven
project (`eval-tasks/testdata/shop`), its four tasks (`eval-tasks/shop.json`) and their
hidden tests (`testdata/shop-*`), keeping the cases and expected values. Validate each:
the hidden tests fail on the fixture, and pass with a reference solution. Then run three
arms on Sonnet: mcp and manual (one run of the harness), and mcp with skeleton-only
creation (a second run, `jrefactor-ab-server-skeleton`).

## Notes

- The fixture, tests and task file by `create_file` (fixture text, outside this project's
  source roots); the build files by `move_file` with `copy`.
- Go-to-Java choices: records for `Order`, `Line`, `Address` (so prompts that change a
  record state its components in order, which the hidden tests depend on); one class per
  export with a static `write`; `Shipping.validAddress` returns the problem or null; JSON
  written by hand, and the hidden JSON tests match its text, since the fixture has no JSON
  library.
- Validation through `bin/jrefactor` on scratch copies: all four fail with the hidden tests
  overlaid; reference solutions pass (duplication's hidden tests pass, and its visible
  `ExportTest` must be updated, as in the Go task). The currency solution was `create_file`
  with `declaration`, `add_parameter` three times and four `replace_text`.
- refusal: the write guard refused an `rm` of a scratch test through a shell variable; the
  test was deleted with `replace_declaration` and empty content.

## Measured

Sonnet, 2 runs per task and arm, `eval-runs/shop-sonnet` (mcp, manual) and
`eval-runs/shop-sonnet-skeleton` (mcp with `JREFACTOR_SKELETON_ONLY=1`), 2026-11-05. Cost per
run (passed, mean turns):

| task | manual | mcp | mcp, skeleton-only |
|---|---|---|---|
| duplication | $0.114 (2/2, 7.5) | $0.220 (2/2, 28.0) | $0.256 (2/2, 39.0) |
| tangled | $0.086 (2/2, 6.0) | $0.097 (2/2, 10.0) | $0.105 (2/2, 11.0) |
| currency | $0.101 (2/2, 5.5) | $0.162 (2/2, 18.0) | $0.279 (2/2, 35.5) |
| address | $0.128 (2/2, 12.0) | $0.202 (2/2, 11.5) | $0.230 (2/2, 17.5) |
| per run | $0.107 (8/8) | $0.170 (8/8) | $0.217 (8/8) |

The tools' extra cost: +59% (mcp), +103% (skeleton-only). All $3.96. A first attempt
(`eval-runs/*-nojdk`, $1.43 for the runs that finished, void) ran the checks without `JAVA_HOME`, so Maven used
the machine's default Java and every check failed ("release version 21 not supported");
the harness must be started with JDK 21 first on the `PATH`.

What the arms did (counted from the transcripts):

- Lines typed into the tools (`content`, `new`): mcp 575, skeleton-only 811. The skeleton
  rule refused 5 whole-file `create_file` calls; each time the model created the type with
  `declaration` and then sent the same members in one `add_declaration`, so the typing was
  redirected, not reduced, and the refused text was paid for twice.
- `test_for` was used in no run of either arm; the tests were typed.
- Skeleton-only address run 1 built `Address` by transformation: `extract_struct` (a
  parameter object) on `Shipping.validAddress`, then `move_type` to `shop.order`.
  `extract_struct` on the `Order` record was refused ("not a top-level type ... that
  resolves").
- A test class started from `declaration` has no imports, so `assertEquals` in the members
  added after became a "not implemented" stub on the test class (`staticImports` imports
  only from a class the file already imports statically from).
- The manual arm worked by Bash (heredocs and scripts) in 7 of 8 runs; its typed text is
  not in these counts.

## Assessment

- **The thesis, on this set:** not supported yet, and not refuted. Every arm passed every
  task; the tools cost more, and skeleton-only more again. The rule worked as a mechanism
  (every refusal was followed by `declaration`), but with only the file-level operation in
  place it moved typing from `create_file` to `add_declaration` instead of replacing it.
  The member-level operations the design note lists (implement, constructor from fields,
  surround with) and use of `test_for` are what would replace typed members; until they
  exist, skeleton-only measures the cost of the refusal, not the vocabulary.
- **The one run that worked at the intended level** (address, skeleton 1) is the evidence for
  the direction: it reached for a transformation (`extract_struct`, `move_type`) to make
  the new type, and was stopped by a gap (records).
- **What went badly:** the void first attempt, from launching without `JAVA_HOME`; the
  session notes now say so on the A/B line.

## Follow-ups

- Roadmap #117 (an assertion method imported, not stubbed, in a test class) and #118
  (`extract_struct` with `fields` on a record), from this run.
- `test_for` unused: the arm's `CLAUDE.md` (`eval-tasks/claude-md/mcp.md`) names
  `create_file` and `add_declaration` but neither option; a run with it named, or with
  `add_declaration` content refused for test methods under the flag, would show whether
  the option is found.
- More runs (4 per arm) before reading the 59% and 103% as more than direction.
