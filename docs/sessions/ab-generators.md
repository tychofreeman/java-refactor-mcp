# A/B: the build generators on the depot tasks (issue #376)

Run 2026-11-13 on `main` at 334e22f, after #423, #429 and #430: gorefactor's harness,
`-model sonnet -arms mcp -runs 2 -tasks eval-tasks/depot.json -claude-md eval-tasks/claude-md`,
once per arm with its wrapper, out `eval-runs/depot-{no-build,build,skeleton,aliases}`.
24 runs, $2.79 in all. The user asked for the run ("then run 2 runs a task").

## Results (means per run)

| arm | wrapper | passed | cost | turns | output tokens |
|---|---|---|---|---|---|
| no `build` ("mcp today") | `-no-build` | 6/6 | $0.129 | 12.7 | 500 |
| `build` | (none) | 6/6 | $0.109 | 10.5 | 471 |
| `build`, skeleton-only | `-skeleton` | 6/6 | $0.113 | 11.3 | 495 |
| aliases published | `-aliases` | 6/6 | $0.113 | 12.2 | 487 |

Per task, cost: reorders $0.16 / $0.13 / $0.14 / $0.13; invoice $0.14 / $0.10 / $0.11 / $0.12;
shipping $0.09 / $0.10 / $0.10 / $0.08 (arms in the table's order).

## What the runs did

- **`build` was never chosen unprompted.** In the `build` and aliases arms every loop and call
  chain was typed in `content` (`insert_statements`, `replace_statements`). It was used only where
  the skeleton-only refusal asked for it: both skeleton reorders runs sent the typed loop, were
  refused, and resent the refusal's `build` at once (one run first also missed `line`, a schema
  error). The skeleton rule did not fire on the invoice task's typed call chain.
- **The create aliases were never called** in the aliases arm; `create_loop`, `create_call` and
  `create_guard` went unused.
- **`replace_parameter` was found by name:** both aliases-arm shipping runs called it, where
  every other arm called `remove_parameter` with `value`. Same arguments, same result, one call.
- **The shipping task** took one refactoring call in all eight runs.
- **The invoice task** was done by hand-typed statements in every arm: `add_declaration` of
  `Order.totalWithShipping` and `replace_statements` in `Invoicing` and `Quotes`.
- No tool errors besides the two above; three Bash calls were refused by the harness's
  permission rules ("A variable in this command can't be checked").

## Reading

- Every arm passed; the differences are within two runs' noise ($0.02 a run, two turns).
  The no-`build` arm was dearest, mostly on the invoice task (17.5 turns against 10.5).
- Publishing `build` as an option does not get it used; only the refusal does, and it costs one
  call. Published names were found when the name matched the job (`replace_parameter`), not for
  the generators.
- Next, if the generators are kept: more runs would be needed to separate the arms; the cheaper
  question is whether the skeleton rule should also refuse a typed call chain.
