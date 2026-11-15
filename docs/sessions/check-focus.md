# check: focused tests by use, `tests`, and a time limit

- **Item:** roadmap #201's follow-up; the user's answers of 2026-11-15 to the questions #628 left
  open: a test is focused when it uses a changed production file (directly, chosen from two
  options); `check` takes `tests` naming the tests to run; a 30 s limit the model can change.
- **Date:** 2026-11-15
- **Branch:** check-focus

## Plan

The change: which tests a check runs follows from what the test code uses, not from the text it
holds, and a run is bounded in time.

1. Green: `add_parameter` of `int seconds` to `Check.Runner.run` and `Check.maven` (the method
   reference's target), one batch, so the runner takes the limit.
2. Red: a test that a nested type's use is focused and a comment's mention is not; a test that
   `tests` and `timeout` reach the runner.
3. Green: `Check.focused` over a `Holding` parse of the test files, by binding (`uses`);
   `Check.tests` reading `tests` and `timeout`; `Check.maven` waiting at most `seconds`, then
   stopping Maven and its descendants, and reading every report the run wrote.

## Notes

- refusal: `add_parameter` on `Check.Runner.run` alone, as the method reference `Check::maven`
  did not take the new parameter: a batch with `add_parameter` on `Check.maven` too.
- refusal: a batch deleting the old `focused` before its caller changed: moved the delete last by
  `retry`.
- hand: the stub `focused(SourceProject, List<Path>)`, declared for the red test, survived the
  real `focused(SourceProject, Collection<Path>)` and safe delete refused it as used; removed by
  `replace_text` (friction log, 2026-11-14 entry "deleting a stub overload").
- bulk: the new `focused`, `uses`, `tests` and `maven` (about 200 lines) in one batch: new
  behavior, `maven` and `tests` replacing their bodies whole since most lines changed.
- probe: on a small Maven project, `tests=["SlowTest","CartTest"] timeout=20` stopped after about
  27 s with no surefire process left; `tests=["CartTest#totals"]` ran the one method;
  `run_tests=true` ran the test using the changed class.

## Measured

Not measured: the CLI session had no `JREFACTOR_LOG` and no hook. From the notes: 8 tool calls
changing Java (6 batches, a rename inside one, 1 `replace_declaration`), 3 refused, one change by
`replace_text` where a tool refused.

## Assessment

- **Through the tools:** every change went through `bin/jrefactor`; the one `replace_text` of
  Java removed a stub safe delete would not.
- **Did the plan hold?** Yes: the parameter by `add_parameter`, red tests with a declared stub,
  then the bodies.
- **One change that would have helped:** `add_declaration` replacing a stub whose parameters the
  new method accepts (friction log).

## Follow-ups

- gorefactor's `check` keeps running the module's tests (the user, 2026-11-15: fine as it is).
