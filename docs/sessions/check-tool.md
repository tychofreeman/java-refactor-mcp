# A check tool, and check as a batch checkpoint

- **Item:** roadmap #201, issue #367 (parity review of 2026-11-13): `check` published (the
  project's compile errors; with `run_tests`, the Maven tests, failures by test and line), and
  `check` as a step of a batch, a checkpoint that keeps the steps before it when a later one is
  refused. The user's comment on #367: run only the tests focused on the changes the batch has
  made so far, however long that takes.
- **Date:** 2026-11-14
- **Branch:** check-tool

## Plan

The change: a batch today is all or nothing, and nothing in the tools runs tests. A checkpoint
splits a batch into a part that is kept and a part that may be rolled back, and the same check
alone tells a session where the project stands (red or green) without leaving the tools.

Derivation:

1. A new class `Check`, with `run` (the tool) and `checkpoint` (the step), on what exists:
   `TypeCheck.errors` for the errors, `Stubs.declare` for a batch checked at its end (the
   checkpoint declares what earlier steps need, as the end check does), `MavenBuild` for the
   command that runs Maven.
2. Which tests: the changed files (a batch's: `Journal.written`; alone: the files of this
   session's changes) give the test classes changed, and the test files that name a top-level
   type of a changed main file. Surefire runs them with `-Dtest=...`; its XML reports give each
   failure's test and line.
3. `Batch.steps` treats a `check` step as a checkpoint: when it passes, it snapshots the files
   written so far. On a later refusal `Batch.run` puts those files back to the snapshot (and the
   ones written after it to their old text) and throws a refusal that `Tools.call` commits rather
   than aborts, so one undo reverts the kept steps; the batch is then not kept for `retry`.

Red first: a unit test of a batch with a passing `check` step then a refused step, which keeps
the first step; then the selection of tests and the reading of a surefire report, as unit tests;
then the tool. The tests that run Maven are left to a stub of the runner (a function passed in),
since a Maven run inside the unit tests is slow and needs the network.

## Notes

- read: `Batch.run`, `Batch.steps`, `Journal.begin/commit/abort/restore`, `Gate.checkedAtEnd`,
  `Gate.apply`, `Tools.call`, `MavenBuild.classpath`: where a batch ends and how a refused call
  restores files.
- read: gorefactor's `checkpoint.go`, for the step's wording and rules.
- plan: which tests run is a rule that could go more than one way; the user's comment fixes the
  intent (focused on the changes) but not the rule, so the one chosen is recorded with the
  options it beat in `docs/design.md`, for the user to change.

- bulk: `Check.java` (316 lines) through `create_file`: new behavior. Sent three times: once
  refused (helpers it needed were private, the session field and tool not yet declared), once in a
  batch refused for an import (`com.sun.tools.javac.comp.Check` chosen for steps before the file
  existed; friction log), once in the retry with the file first.
- hand: none in Java. `Batch` was changed by `replace_text` steps (nine, one batch) where
  `add_parameter` would have carried the new parameters of `steps` to its two calls; the calls
  changed in the same batch anyway, and `boolean atEnd` became a map, which is `change_type`
  plus `add_parameter` plus the body's uses: three steps for what one replacement said.
- refusal: `ServerTest.definitionsStaySmall` refused a sentence added to `batch`'s description
  (1,890 over its ratchet of 1,801): the sentence went into `check`'s own description instead.
- plan: `check` is `Kind.READ`, not `CONTROL`: it writes no source, and `SessionReportTest` counts
  every other tool as a change.
- probe: on a small Maven project, `check run_tests=true` after an `add_declaration` ran only the
  test naming the changed class and named its failure `p.CartTest.totals (CartTest.java:10)`.

## Measured

Not measured: the CLI session had no `JREFACTOR_LOG` and no hook (no `.claude/session-log`).
From the notes: 6 tool calls changing Java (2 `create_file`, 3 batches, 2 `replace_text`), 4 refused
(one `create_file`, two batches, one `replace_text` with a line range), none by hand.

## Assessment

- **Through the tools:** every Java change went through `bin/jrefactor`. The plan held: one
  new class, the checkpoint as a step `Batch.steps` treats itself, the kept refusal committed by
  `Tools.call`.
- **Large writes:** `Check.java` was new behavior, sent whole because nothing existed to move;
  resending it twice was the cost of the refusals above, about 600 lines of input.
- **TDD:** the checkpoint tests were red first (no tool `check`); the tests of `focused`,
  `report` and the tool were written after the code and passed at once, so they pin it rather
  than drove it.
- **Reading:** the bodies of `Batch.run`, `Batch.steps`, `Journal`'s transaction methods and
  `Gate.checkedAtEnd`, to write the new behavior; none to find uses.
- **One change that would have helped:** the import fix preferring a type the same batch
  creates later (friction log, 2026-11-14).

## Follow-ups

- The rule for which tests run is a default (`docs/design.md`, Decisions): the user may prefer
  bindings or an explicit list.
- `examples/CLAUDE.md` and `eval-tasks/claude-md/mcp.md` do not mention `check` yet: they change
  after an A/B run shows the checkpoint helps.
- gorefactor's `check` runs the module's tests (`./...`); whether it should focus as this one
  does is a parity question for the user.
