# A/B: the constructive task set

- **Item:** the user, 2026-11-06: run the new task set (`eval-tasks/library.json`, built in #252)
  to see whether the constructive operations are used when the tasks call for them.
- **Date:** 2026-11-06
- **Branch:** ab-library

## What ran

Sonnet, 2 runs a task, three arms: manual and mcp (`eval-runs/library-sonnet`), and mcp with
skeleton-only creation (`eval-runs/library-sonnet-skeleton`). `main` after #252. Cost $2.36.

## Results

| Arm | Passed | Cost per run | Turns | Typed lines | Tool errors |
|---|---|---|---|---|---|
| manual | 8/8 | $0.082 | 9.5 | | |
| mcp | 6/8 | $0.097 | 9.6 | 288 | 13 |
| mcp, skeleton-only | 7/8 | $0.117 | 13.8 | 396 | 19 |

By task (manual / mcp / skeleton, cost per run): sms $0.070 / $0.092 (1 of 2) / $0.115 (1 of 2);
reservation $0.080 / $0.095 (1 of 2) / $0.127; missing-file $0.093 / $0.097 / $0.105; fines
$0.084 / $0.102 / $0.122.

## What the models called

- `implement`, `constructor` and `surround`: none, in any run of either tool arm.
- `test_for` once (skeleton, a `create_file`); `declaration` 4 times (skeleton).
- The three failures are one pattern: the model wrote both files with shell heredocs (`cat >
  ... <<'EOF'`), the A/B's write guard refused them (6 and 5 refused writes in the two logs), the
  model tried `Write` (not available) and `sed` into the file, and ended without calling a
  jrefactor tool; the class was never created, and the hidden test did not compile. In the
  skeleton run the guard's refusal named the tools, and the model still did not reach for them.

## Assessment

- **The constructive options are not found.** With tasks that call for them (a new
  implementation of an interface, a class from its fields, a try around a call), no model used
  `implement`, `constructor` or `surround`: they are options inside long descriptions of
  `add_declaration` and `replace_statements`, and a model that wants a class writes it.
  `declaration` was used only under the skeleton rule, which refuses the alternative.
- **The tools cost more here, and lose runs to the guard.** Manual is cheapest and passed every
  run; the mcp arms lost 3 runs where the model never used the tools at all. On these small tasks
  the tools' overhead (reading schemas, outlines) is not paid back.
- **The thesis, on this set:** not supported. The mechanism that changes behavior is a refusal
  (skeleton-only); descriptions do not, as gorefactor's notes say ("mechanisms over guidance").
  Measuring the operations would take a mechanism that steers to them: a refusal of a typed
  constructor or try that names `constructor` or `surround`, as skeleton-only does for whole
  files, or a mention in the arm's instructions.

## Follow-ups

- A refusal under skeleton-only of `add_declaration` content that is a constructor only assigning
  fields (name `constructor`), and of `replace_statements` content that wraps the old statements
  in a try (name `surround`): the same mechanism that made `declaration` used. Ask before
  building: it widens an experiment flag.
- The write guard's refusal in the A/B arms could name `create_file` with `declaration` and
  `add_declaration` for the file being written, so a model that typed a heredoc has the call to
  make.
