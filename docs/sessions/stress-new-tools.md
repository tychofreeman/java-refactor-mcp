# Stress the five new refactorings

- **Item:** add `extract_variable`, `inline_variable`, `inline_function`,
  `pull_up` and `push_down` (merged by the other session, 2026-11-04) to
  `RefactoringStressTest`, and run it on Commons Lang and Math. Session
  notes: a tool whose rules are heuristics gets a stress run on a real
  project; these five had none. The user chose it over nested subclasses
  for pull up and push down, and the A/B run.
- **Date:** 2026-11-04
- **Branch:** stress-new-tools

## Plan

The change: the stress test picks targets for five more tools. It already
checks what each must keep (apply or refuse, no added error by a full
count, the narrowed count agrees, undo restores), so only targets are new.

1. `add_parameter` `RefactoringStressTest.target(String, String,
   CompilationUnit, Random)`: a `String text` after `unit`, the value `text`
   at the one call. `extract_variable` needs the expression's source text,
   which the unit does not keep.
2. `replace_text`: five cases in `target`'s switch, before `default`:
   - `extract_variable`: an infix expression, method call, cast, field or
     array access whose statement is directly in a block; its line, its text.
   - `inline_variable`: a fragment of a local declaration with an initializer.
   - `inline_function`: a static method with a body, of a top-level class.
   - `pull_up`, `push_down`: a method or field of a top-level class
     (`pull_up`: one that extends a class), as `Type.member`.
3. `replace_text`: the five names in `TOOLS`.

Then run it with a few seeds on each project, and read what fails or is
gated. Each failure is its own item (fix in the tool, with a golden case),
unless it is small and in this test.

## Notes

- `bulk:` the five cases (about 90 lines) by `replace_text` in one batch with
  `add_parameter` and `add_declaration` (`statementOf`): new behavior.
- `refusal:` "Expression could be java.beans.Expression, ..." for the batch's
  `replace_text`; sent again with `imports`. Then the same for
  `MethodInvocation`: the refusal names one ambiguous name per call, so the
  third send passed every `dom` name the cases use. Its suggestion for
  `MethodInvocation` was JUnit's `org.junit.jupiter.engine.execution`
  class, not the `dom` one the file already imports from.
- `refusal:` "old is not in" for the `TOOLS` list: I wrote 12 spaces of
  indentation where the file has 10, counting `inspect`'s separator again.
  Sent again with 10.
- `plan:` the first run (Lang, 80 calls, random tool per try) called
  `pull_up` never and the others mostly on targets they refuse (no
  subclass, calls in the expression). Added a second batch: tools in turn
  (`made % TOOLS.size()`), a `Hierarchy` record (the project's class names
  and the names they extend) passed to `target` by `add_parameter`, and a
  `pure` helper (no call, assignment, increment or creation) for
  `extract_variable` and `inline_variable`; `inline_function` takes
  one-statement bodies only.
- `read:` `InlineFunction.run` and `replacementFor` (lines 95-260), to see
  which argument checks exist: only "used twice with an effect". That was
  reading to judge whether the tool is safe, which the stress test cannot
  tell.
- Probes (`bin/jrefactor` on scratch projects in the scratchpad), each applied
  and type-checked while changing behavior:
  - `inline_function` `Lib.one(next())` with `one` ignoring `x` gave `1`:
    the call to `next()` is gone.
  - `Lib.half(3)` with `double half(double x) { return x / 2; }` gave
    `(3 / 2)`: integer division.
  - `Lib.sub(next(), next())` with `return b - a` gave `next() - next()`:
    the calls run in the other order.
  - `Lib.scale(1)` with `return x * FACTOR` gave `1 * FACTOR` in a class
    with its own `FACTOR` (10, not 2).
  - `extract_variable` of `i * 2` in `return i++ + (i * 2)` declared it
    before the statement, so it reads `i` before the increment. (The use is
    left as `(twice)`.)
  - `extract_variable` named `total` in a method that later reads the field
    `total`: the local hides it.
  - `inline_variable` of `int a = n;` used inside a block declaring a local
    `n` gave `n + n`.
  - `pull_up` of `Sub.name()` into `Base extends Root`, where `Root`
    declares `name()`: `Other extends Base` now returns "sub".
  - `pull_up` into `class Base extends Root {}` (one line): a syntax error,
    refused as "Move only a member that uses nothing the subclass has".

## Measured

From `bin/session-report` (the branch's log):

- tool calls: 43
- through jrefactor: 2 refactoring steps, 12 edit steps
- refused by jrefactor: 3
- by hand: 12, none of them Java in the project: the record, the docs
  (design, friction log, session notes), Maven runs, and the probe
  projects in the scratchpad (written with heredocs)
- refused, then done by hand: 0
- large writes: 5. Three are the stress-test cases (92, 28, 21 lines, by
  `replace_text` and `add_declaration`), two the probe projects (41 and
  29 lines, heredocs in the scratchpad)
- written to scratch files: 0 (the probes went by a heredoc in Bash, which
  the report counts as by hand)
- denied or failed: 1 (a Python edit of `docs/design.md` that applied; the
  report's line is the hook's)
- reads: Bash java 7, inspect 5

## Assessment

- **Through the tools or around them:** through. Every change to Java in
  the repo was a tool step, in two batches. The three refusals were
  answered by sending again: twice with `imports`, once with the right
  indentation. The probes' Java was written by heredoc because the probe
  projects are throwaway fixtures outside the repo; `create_file` there
  would have needed a CLI call per file, with no gain.
- **Large writes:** the 92-line `replace_text` is the five new cases, new
  behavior. `Hierarchy` (28) and `pure` (21) are new too.
- **Did the plan hold?** Steps 1-3 were used as planned. The plan did not
  foresee the weighting (second batch, a `plan:` note), and its "read
  what fails" found nothing, since nothing failed. What found the problems
  was reading `InlineFunction` and probing by hand.
- **Reading:** one body, `InlineFunction`, to judge safety, the kind of
  question the tool should answer itself.
- **One change that would have helped:** an oracle for behavior. The stress
  test checks compile errors, so a refactoring that compiles and changes
  meaning passes. For the name cases, a check that every name in moved text
  resolves to the same binding after the edit would catch them in the tool
  itself (roadmap #67, friction log 2026-11-04). The ambiguous-import
  refusal listing all names at once would have saved two calls (friction
  log).

## Follow-ups

- Roadmap #67 (`inline_function`), #68 (`extract_variable`,
  `inline_variable`), #69 (`pull_up` overriding), #70 (`pull_up` into `{}`).
  These tools are the cloud session's (the ten higher-level tools); which
  session fixes them is the user's call.
- The stress test's turn-taking waits on a tool with no target until the
  try limit (`calls * 200`), so a project with no class hierarchy spends
  its tries on `pull_up`. Not seen on Lang or Math.
- `push_down` into a one-line `{}` subclass was not probed.
