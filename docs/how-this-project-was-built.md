# How this project was built

A record of the practices behind java-refactor-mcp, written to decide which
are worth repeating, as gorefactor's `docs/how-this-project-was-built.md`
does for the Go project. It draws on the git history, `docs/friction-log.md`,
`docs/session-notes.md`, `docs/design.md` and the records in `docs/sessions/`.
It was started on 2026-11-04, covering the work from 2026-11-02 (PRs #1-#103), and
extended the same day through PR #130.
Judgments are Claude's; figures are from the records named beside them.
Extend it in the PR that changes a decision, as gorefactor's was.

## The arc in brief

1. **2026-11-02: the safe core.** JDT Core headless, a type-check gate
   (`TypeCheck`, `Gate`) that refuses any change which adds a compile error,
   `rename`, and the MCP server over STDIO. The spike
   (`spikes/jdt-headless/`) showed bindings resolve with no workspace.
2. **2026-11-02: edits and their support.** `create_file`, `replace_text`,
   `add_declaration`, `replace_declaration`, with imports fixed by every edit
   (`Imports.fix`) and stubs declared for what an edit uses (`Stubs`).
   `batch` and `undo` came with a journal, so every write is undoable.
3. **2026-11-02 to 11-03: signatures, moves and extraction.** `add_parameter`,
   `remove_parameter`, `move_parameter` across overrides and implementations;
   `extract_method`; `move_type`; `pull_up` and `push_down`, for methods,
   fields and overloads (PRs #99-#102).
4. **2026-11-03: performance and measurement.** A narrowed type check (#56,
   after a stress run found its first rule missed three of 60 random edits
   through inheritance); a classpath cache; `bin/jrefactor` for sessions
   where the server cannot run; a session log of every tool call. Measured
   costs are in `docs/sessions/measure-call-cost.md` and
   `docs/sessions/cli-cache.md`.
5. **2026-11-04: the process was checked.** The roadmap's open items were
   closed or split, and the tool-count limit was set at 50 with `ToolsTest`
   (PR #103). A comparison with gorefactor (Go and TypeScript), Fowler's
   catalog and IntelliJ found about 35 refactorings that are composites of
   our tools, most of them through `replace_text`.
6. **2026-11-04: the ten higher-level tools** (PRs #105-#115), one PR each,
   in the user's order: `extract_variable`, `inline_variable`,
   `inline_function`, `change_type`, `add_supertype`, `move_statement`,
   `invert_if`, `remove_else`, `move_member`, `encapsulate_field`. Each was
   written by hand: no tool writes a new tool.
7. **2026-11-04: behavior that type-checks.** Probes after the stress run
   found seven rewrites that compiled and changed behavior (effect order,
   name capture). They became refusals (#116, #119, and later read-order and
   pull-up fixes). Then the user set the policy on what a refactoring must
   preserve (below), and a review of the code's abstractions put two shared
   shapes ahead of the flag that policy needed (#71, #72, then #73).

## Why `replace_text` carried so much

This is the record the user asked for on 2026-11-04. Three things explain it.

- **It was the only edit that took text.** `replace_text` was built in
  roadmap #19, with `create_file`, `add_declaration` and
  `replace_declaration`. There was no edit addressed by a node in the
  syntax tree. Anything inside a body that was not a whole declaration could
  only be changed by replacing its text, so every composite that touched an
  expression, a statement or a call site reached for it.
- **The rule written down allowed it.** CLAUDE.md says `replace_text` is for
  "a few lines, literals, comments and non-Java files". Literals and short
  edits inside bodies fell under "a few lines", so the wide uses were not
  against the rule as written. The rule did not say that a composite would
  be built from it.
- **It passed the gate, so nothing failed.** A `replace_text` that left the
  code compiling was accepted. The cost was not visible in a result: the
  model wrote the old text and the new, and the gate could not tell which
  occurrence was meant. The cost shows only in tokens and in review.

We did not measure how often it was used for a composite. The friction log
was not searched for it before the 2026-11-04 comparison, so the claim
"most of them through `replace_text`" is from reading the composites, not
from counts. A count is the first thing to add to the records.

## The decision: ten higher-level tools

On 2026-11-04 the options were:

- **A. Three primitives:** `replace_expression`, `insert_statements`,
  `remove_statement`. Cheapest in tools. Each composite stays a batch of
  text-free but still hand-written steps.
- **B. One tool per refactoring:** `extract_variable`, `inline_variable`,
  `inline_function`, `change_type`, `add_supertype`, `move_statement`,
  `invert_if`, `remove_else`, `move_member`, `encapsulate_field`. The
  most tokens saved, and the most tools to test. Chosen by the user, who
  wants the model to work "at a higher level of abstraction".
- **C. Primitives first, then refactorings on friction-log evidence.** The
  lowest tool count, and the one CLAUDE.md's evidence rule favours. Not
  chosen.

The user chose **B** over **A** and **C**, and raised the tool-count limit
to 50 to allow it. Under B, each tool is its own PR, in the order the user
gives, with golden cases first. The names are the working list from the
comparison, not final: each tool's design note decides its name and
arguments.

The choice is in tension with CLAUDE.md's "do not add tools speculatively".
Ten tools are not speculative if the composites in the comparison are
real uses, but the friction log has not yet been counted to show that.
That count is the first step before the first of the ten is merged, and
the result goes in the first tool's record.

## The stress run sees compile errors, not behavior

`RefactoringStressTest` ran 220 calls of the new tools on Commons Lang and
Math with no failure, and probes written by hand then found seven changes in
behavior that type-checked (`docs/sessions/stress-new-tools.md`): an
argument's call dropped, two calls reordered, `half(3)` turned into integer
division, a name binding to another class's field, a read moved ahead of an
increment, a local hiding a field. The gate cannot see these, by design. The
lesson, recorded in the session notes: a tool that moves expressions or
members needs probes for evaluation order and name capture, not only a
stress run. The fixes were refusals, not rewrites, and are conservative: a
safe case of the same shape is refused too.

Two sessions fixed the same roadmap row (#67) at once that day. The user
kept one session's refusals and closed the other's PR (#118). Since then,
a session checks `main` and the open branches for a row before fixing it.

## What a refactoring preserves

On 2026-11-04 the user set the rule, as an extension of "behavior-preserving
transformation": no transformation keeps every kind of behavior, so a
refactoring is defined by the kinds it keeps. Normal logic flow (calls,
assignments, reads and their order) is kept. Exceptional behavior is kept
only where a guard or a try/catch makes it part of the normal logic;
elsewhere it is a concession, made knowingly and reported. The model can
require it with `preserve_exceptions`, and a tool that changes it without
the flag names each change in a separate part of its result. The options
the user chose between (one flag or one per kind; the alert in the text or
apart) are in `docs/design.md`, question 0.

## Abstractions before the cross-cutting flag

Building the flag on the code as it was would have meant editing fourteen
`run` methods and every tool schema, and adding a fifth partial model of
effects. A review of the abstractions (2026-11-04) found the infrastructure
well factored (`Jdt`, `Gate`, `Journal`, `TextEdit`, `Lines`) and the tool
layer without a shared shape: each tool parsed and located its target its
own way and refused from inside its algorithm. The user agreed to build two
abstractions first, each behavior-neutral and checked by the goldens:
`Opened`/`Precondition` (#71) and one `Effects` analysis (#72). The flag
(#73) then attached once, in the `Def` constructor. All three were done by
another session the same evening, through the tools.

## Through the tools, or around them

The ten tools and the effect-order refusals were written by hand, by Edit
and Write, in a session that had no java-refactor-mcp tools loaded. One of
its records first called that work "through the tools"; it was corrected
(#123). The sessions that built #71-#73 had the tools and made almost every
Java change through them (21, 19 and 53 edit steps, plus `add_parameter`
and `inline_function`, by `bin/session-report`). That is the first
evidence in this project of the difference the user asked about: whether a
session thinks about changes as operations when it has the tools, or as
text when it does not. It is not yet a measurement of token cost; the A/B
harness (roadmap #13) is.

## What every session loads

By the evening of 2026-11-04 every session loaded `CLAUDE.md` (222 lines) and
the session notes it imports (561 lines, about 5,900 words) before doing
anything. Most of the notes were a history of each item ("X exists, roadmap
#N, date, measured Y"), written there because the notes were the first place
sessions recorded what they learned, before the records and this document
existed. The user agreed to prune both (#131 and the PR after it): the notes
now hold only preferences, the user's decisions, rules for where code goes,
testing practice and traps (157 lines), and say that history goes in the
records and here. `CLAUDE.md` lost its tool lists, which named 13 of 25 tools,
and the text it said twice.

## Open at the time of writing

- `CLAUDE.md` still lists literals as a `replace_text` use; option B's
  `replace_expression` was not built, so the line stands until a tool covers
  literals.
- `docs/friction-log.md` was never counted for `replace_text` use before the
  ten tools were built; the claim in "Why `replace_text` carried so much"
  rests on reading the composites.
- Follow-ups that lived only in records are now roadmap rows #74-#79.
- The A/B harness (roadmap #13) waits for the user's yes, since it costs money.
