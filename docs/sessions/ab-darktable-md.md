# examples/CLAUDE.md against the darktable project's CLAUDE.md

- **Item:** the user, 2026-11-13: compare `examples/CLAUDE.md` with the CLAUDE.md of a project
  that uses the tools (`examples/darktable-project-CLAUDE.md`), first statically, then by an A/B
  run.
- **Date:** 2026-11-13
- **Branch:** ab-darktable-md

## Static comparison

| | `examples/CLAUDE.md` | darktable's |
|---|---|---|
| Size | 25 lines, about 3,600 characters | 164 lines, about 9,000 (87 without the project's own parts) |
| TDD order | The same four steps; step 2 names `create_file` with `test_for` | The same, each step labelled green or red; "even code that duplicates", "undo the test" |
| Work as a derivation | The same text | The same text |
| Tool guidance | One paragraph each for refactorings, edits and reading | Six numbered rules, this repo's CLAUDE.md's: the same, plus a `batch` with check `"end"` and `undo`, "read refusals; they say what to send", `replace_text`, the two spaces of `inspect` |
| Project context | None | What the program does, the build environment, fixtures, decisions, branch and PR rules |

The two agree wherever both speak; darktable's adds. The additions a run could feel are the
batch and refusal rules.

## The run

A copy of darktable's file fitted to the fixtures (`eval-tasks/claude-md-darktable/mcp.md`): its
project description, branch and CI rules, "let the user choose" (a headless run would stall), the
server's location, the CLI, the build environment, fixtures and decisions taken out; the test
command the current file names; the rest verbatim. The mcp arm (`jrefactor-ab-server`), Sonnet,
two runs a task, on `shop.json` and `warehouse.json`, from `main` at cd39cb2; out
`eval-runs/ab-darktable-md/{shop,warehouse}-{examples,darktable}`. All 32 runs passed; $4.95.

| | examples | darktable |
|---|---|---|
| shop, a run | $0.204, 21.8 turns | $0.195, 18.0 turns |
| warehouse, a run | $0.112, 10.5 turns | $0.108, 9.5 turns |
| `batch` calls (shop, warehouse) | 8, 1 | 28, 7 |
| other edit and refactoring calls | 56, 31 | 15, 17 |
| refused calls | 6, 7 | 15, 6 |

| task | examples (run 1, run 2) | darktable |
|---|---|---|
| shop-address | $0.23/14, $0.23/20 | $0.21/17, $0.43/32 |
| shop-currency | $0.16/18, $0.18/21 | $0.15/15, $0.16/19 |
| shop-duplication | $0.29/32, $0.26/37 | $0.18/21, $0.19/14 |
| shop-tangled | $0.16/18, $0.13/14 | $0.12/13, $0.12/13 |
| warehouse-dedupe | $0.17/16, $0.20/9 | $0.13/11, $0.16/10 |
| warehouse-inline | $0.07/8, $0.08/8 | $0.10/9, $0.08/6 |
| warehouse-rename | $0.09/10, $0.09/10 | $0.09/10, $0.09/10 |
| warehouse-reorder | $0.11/12, $0.10/11 | $0.10/10, $0.10/10 |

## Reading

- The batch rule is the difference that shows: the darktable runs sent three to seven times as
  many batches, and fewer calls in all (shop 136 against 166, warehouse 68 against 76).
- shop-duplication is the one task where both runs differ beyond noise: $0.18-0.19 and 14-21
  turns against $0.26-0.29 and 32-37.
- The dearest run (shop-address, darktable, run 2, $0.43) sent ten refused batches. It rewrote
  `Order` by `replace_declaration` and left a constructor not calling the canonical one, then
  tried `remove_parameter` of each record component, refused as "Order declares no constructor.
  Add one with add_declaration (constructor)", which does not fit a record. `extract_type` with a
  record's components does the task; the examples arm's runs called it, this run did not. That
  refusal is roadmap #197.
- Reading was alike: `inspect` throughout, no Read of Java.
- Runs on both files tried to log to `docs/friction-log.md`, which the fixtures do not have.
- Two runs a task: the totals are within noise, except shop-duplication.

## Decision

The user, 2026-11-13: add both rules (a batch with check `"end"` for dependent steps, and read
the refusal) to `examples/CLAUDE.md` and `eval-tasks/claude-md/mcp.md`. This repo's CLAUDE.md
already has them (items 5 and 6 of "Using java-refactor-mcp"); darktable's took them from there.
The rest of darktable's additions are left out: nothing measured asks for them.

## Follow-ups

- A rerun of the shop tasks with the updated file (about $2) would show whether the rules alone
  carry the batching.
- Roadmap #197: `remove_parameter` of a record's component.
