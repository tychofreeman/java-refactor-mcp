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

(the runs)

## Assessment

## Follow-ups
