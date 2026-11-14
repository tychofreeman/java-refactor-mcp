# The A/B arm without build

- **Item:** issue #376's first arm, "mcp today": a server whose statement edits take typed code
  only, now that `build` is merged. The user, 2026-11-13: a server flag (`JREFACTOR_NO_BUILD`), over
  the jar from before #149 (every fix since would differ too) and over dropping the arm.
- **Date:** 2026-11-13
- **Branch:** no-build-arm

## Plan

- `Session.noBuild` from `JREFACTOR_NO_BUILD=1`, as `skeletonOnly` is set.
- `Tools.published(session)`: `ALL`, or under the flag `ALL` with `insert_statements`,
  `replace_statements` and `replace_expression` replaced by `Def`s with their arguments and text as
  before #149, #163 and #165 (`Tools.TYPED`; `content` and `new` required again, `surround` a
  string). The server publishes it.
- `Tools.named(name, session)`: the same `Def`s by name, for a batch's steps, and a refusal for
  `create_loop`, `create_call` and `create_guard`, which are one `build` entry each.
  `replace_parameter` stays: it is `remove_parameter` with `value`, not a generator.
- A `build` sent anyway is refused by the argument check the typed `Def`s carry ("replace_statements
  does not take build"), so no new refusal was written.
- The wrapper `eval-tasks/jrefactor-ab-server-no-build`, as the skeleton one.

Tests: `PublishedToolsTest` (the published list and lookup by name, with and without the flag),
golden `batch_no_build_refused` (`batch_build_method`'s steps, refused under `no_build=true`,
which `GoldenTest` sets as it sets `skeleton_only`).

## Notes

- Refactor after green: `named(String, Session)` had copied `named`'s client-name parsing and its
  refusal. It now resolves through `named` first and tests the `Def`'s name; the refusal is
  `noTool(name)`, from `extract_variable`, `extract_function`, two `inline`s and `fold` onto the
  copy.
- The CLI is not changed: it resolves the tool before it loads a project, and the A/B harness runs
  the server.
- A docs batch was refused for an `old` that was not the roadmap's last row (read as two lines
  short); its `retry` was refused too, the CLI having kept no batch after the runs between, so the
  batch was sent again whole.