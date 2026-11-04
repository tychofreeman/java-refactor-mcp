# How this project was built

A record of the practices behind java-refactor-mcp, written to decide which
are worth repeating, as gorefactor's `docs/how-this-project-was-built.md`
does for the Go project. It draws on the git history, `docs/friction-log.md`,
`docs/session-notes.md`, `docs/design.md` and the records in `docs/sessions/`.
It was started on 2026-11-04, covering the work from 2026-11-02 (PRs #1-#103).
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

## Open at the time of writing

- The first of the ten tools will remove the "literals" line from CLAUDE.md
  as well, since literals are an `replace_expression` case under B.
- `docs/friction-log.md` has not been counted for `replace_text` use.
- The ten tools are not built yet. Only the tool-count limit (roadmap #15)
  is in.
