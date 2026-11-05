# Behavior probes as a test

- **Item:** the follow-up of `docs/sessions/probe-fold-binding.md` and roadmap #16:
  the probe harness of #186, which found a behavior change that compiled in `fold`,
  kept in the repository so that every tool's probes run on every build. The user's
  choice of 2026-11-05 ("Probe harness in repo").
- **Date:** 2026-11-05
- **Branch:** probe-test

## Plan

- `ProbeTest`, run with the other tests (not opt-in: a probe needs no outside
  project, and compiling and running one in-process takes milliseconds). Each
  directory under `src/test/resources/probes` is a case: `in/` (a project whose
  `p.Main` prints what it did), `args.properties` (the call, as a golden case's), and
  `probe.properties` (`expect`: `applied`, `refused`, or `changed` for an edit that
  is meant to change the output).
- For a case: compile `in/` with the system compiler and run `p.Main.main` in a
  fresh class loader, capturing its output; call the tool; if it applied, compile and
  run again; `applied` requires the same output, `changed` a different one.
- Reuse `GoldenTest`'s `call`, `copyTree` and `deleteTree` (package-private), not
  copies of them.
- The 21 probes of #186 as cases, with their outcomes today; one `replace_text` probe
  expected `changed`, which shows the harness sees a change.

## Notes

- The 22 cases were written by a script into one batch of `create_file` steps,
  sent through `bin/jrefactor @file` (the `tool` key of #189); the session report
  counts that one shell call as by hand.
- The read guard refused a `grep` of `Fold.java`; the mutation below went through
  `replace_text` and `undo` instead.
- **The probes catch the bug they were made from:** with `fold`'s overload check
  disabled (`replace_text`), `fold_overload` failed ("applied ... expected
  refused"); then `undo`. The output comparison now runs before the expectation, so
  such a failure says "behavior changed" first.
- 22 cases, 1.9 s: run with every build, not opt-in.

## Measured

From `bin/session-report`: 13 tool calls, 7 edit steps through jrefactor, no
refusals; one large write, `ProbeTest` (132 lines, new); one Bash call counted by
hand (the batch through `bin/jrefactor`, above).

## Assessment

- **Through the tools or around them:** through; the cases' text came from a
  script, as the probes' code was already written.
- **Did the plan hold?** Yes; `GoldenTest`'s helpers are shared, not copied.
- **One change that would have helped:** `bin/session-report` counting a
  `bin/jrefactor` call as a tool call, not by hand (friction log).

## Follow-ups

- Probes for the other tools that move code (`extract_method`, `move_member`,
  `pull_up`, `push_down`, `convert_lambda`, `move_statement`), from
  `docs/sessions/stress-new-tools.md`'s probes.
