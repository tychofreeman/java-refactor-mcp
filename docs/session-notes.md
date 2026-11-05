# Session notes

What sessions working on this project have learned that the code, the README and
the other docs do not record: the user's preferences and decisions, rules for
where code goes, and traps. Every session reads this (CLAUDE.md imports it), so
keep it to what a session needs now: facts, with the reason, not a diary. History
and measurements go in the item's record (`docs/sessions/`) and in
`docs/how-this-project-was-built.md`; the roadmap is in `docs/design.md`. When a
fact here becomes wrong, fix or remove it in the same PR.

## Working with the user

- One item per branch and PR (adopted from gorefactor, 2026-11-02).
- Claude merges its own PRs once CI is green and they are mergeable; use merge
  commits, then delete the PR's branch (77 merged branches had piled up by
  2026-11-04 and were removed).
- Stacked PRs: retarget the upper PR to `main` *before* merging the lower one
  with `--delete-branch`. Deleting the base branch first closed #4, and a closed
  PR whose base is gone cannot be reopened or retargeted.
- Development standards follow gorefactor's (`CLAUDE.md` there, and its
  `docs/how-this-project-was-built.md`), with the same goal: about 95% of coding
  through the tools, judged by token cost against editing by hand.
- "What's next?" runs the `whats-next` skill. Its record per item lets the user
  judge whether a session used the tools or worked around them; the session log
  (`bin/session-log`, `bin/session-report`) is the measured part.
- Before fixing a roadmap row, check `main` and the open branches for the same
  row: two sessions once fixed #67 at once, and one PR (#118) was closed.
- The roadmap's open items are ordered by evidence (gorefactor's, then this
  repo's friction log), not only by what unblocks what; the reasons are under
  "Why this order" in `docs/design.md`.

## Decisions the user made

Each was chosen from options; the record named beside it has the options it beat.

- What a refactoring preserves (2026-11-04, `docs/design.md`, Decisions): every
  effect in the normal logic flow. Exceptional behavior is kept only under a
  guard or a try/catch; elsewhere a tool may change it and names each change.
  `preserve_exceptions` (one flag on every refactoring) makes it refuse instead;
  the changes are a separate part of the result.
- Stubs: declared automatically by edits, no `create_from_usage` tool. A class
  the same call created gets no default-constructor stub. A lambda argument to
  an undefined method gets a functional interface by its arity (`Supplier`,
  `Function`, `BiFunction`), a guess. Stub type variables are written by name
  when the target type declares them, erased otherwise.
- `rename` is one tool for every kind of name. When a line has two meanings it
  refuses, and `name` picks one (`Counter.count`, `local count`); not "the local
  wins".
- `extract_method` refuses two or more results rather than returning a record,
  and the new method is static exactly when the enclosing member is.
- `move_type` is its own tool, and widens private members used across the
  boundary rather than refusing.
- A failed Maven classpath keeps the server serving and names the cause in
  refusals that say a name cannot be resolved.
- The ten higher-level tools (option B, 2026-11-04), with the tool-count limit
  raised to 50 (`ToolsTest`). Within them: `move_statement` is strict by
  default (no shared written name, at most one side effect) with `names_only`
  to relax it; `invert_if` simplifies the negated comparison; `move_member`
  passes the instance as a `receiver` parameter; `encapsulate_field` writes
  bean-style accessors with optional names.
- Order 32 (one edit pipeline) is closed as partly done: `SignatureChange` and
  `MoveType` keep their own per-file import fix and gate.

## Where code goes

- `Jdt` owns every parser; `grep ASTParser` finds `Jdt` only.
- `Journal.put` and `Journal.restore` are the one writer of project files.
- `Edits.gated` is the one place a single-file edit fixes imports and passes the
  gate; add new single-file edits through it. It fixes imports only for a file
  under a source root.
- `Session` holds all per-project state (undo history, deferred flag, library
  index, last type check, `editing`, `preserveExceptions`); there is no static
  mutable state. `Server.Quiet` holds the per-server state.
- A new tool starts from `Opened` (read, parse, find the target) and lists its
  up-front checks as `Precondition`s; mid-algorithm refusals stay `throw`
  (`docs/sessions/refactoring-shape.md`). `Rename`, `MoveType`, `Extract`,
  `Declarations` and the signature tools keep their own resolvers (roadmap #74).
- What code reads, writes and calls comes from `Effects.of(node)`
  (`docs/sessions/one-effects-analysis.md`); a new effect kind goes in
  `Effects.Kind`. `Effects.mayThrow()` is the syntax-only "may throw".
- A refactoring reports or refuses a change to exceptional behavior through
  `Exceptional.change`, or keeps it and reports through `Exceptional.report`.
  Every `Kind.REFACTORING` `Def` has `preserve_exceptions` from its constructor.
  Goldens pin the separate part of the result in `exceptional.txt`; a case
  without that file must report nothing.
- A tool's result is an `Outcome`; `render()` gives the text the model reads,
  with the stubs named after it.
- Stubs are declared only for edits (`Session.editing`, set for `Kind.EDIT`); a
  refactoring that leaves a name unresolved is refused. A stub goes in the
  source root of the file that uses it.
- The session report counts by `Tools.Def.kind`, so a new tool needs only its
  `Kind`.
- The server runs one tool call at a time (`Server.ONE_AT_A_TIME`): two edits
  run at once undid each other. It exits when stdin ends only after it is quiet,
  since the SDK drops requests it has read but not run.

## Testing

- Every golden write case is undone by `undo` and must return to `in/`, and the
  cached type count must equal `TypeCheck.uncached`.
- A rule in the gate that skips work (such as the narrowed check) is checked by
  a stress run on a real project before it is merged: the narrowed check's first
  rule missed three of 60 random edits, all through inheritance, though every
  example test passed. `NarrowCheckStressTest` and `RefactoringStressTest` are
  opt-in (`-Djrefactor.stress.root`, `.seed`, `.counts`); a killed run is put
  back by the next one.
- The stress tests see only compile errors. A tool that moves expressions or
  members also needs probes for evaluation order and name capture: probes found
  seven behavior changes that type-checked (`docs/sessions/stress-new-tools.md`).
  The refusals that followed are conservative, and compare first uses only.
- Measure call costs with `GateCostTest` (`-Djrefactor.cost.root`,
  `-Djrefactor.cost.call`), not from old figures.

## Traps

- `gh pr checks` right after `gh pr create` can say "no checks reported" before
  CI registers, and `--watch` then returns at once. Wait for the run. A PR that
  conflicts with `main` gets no run at all: check its `mergeable` state.
- GitHub's GraphQL API is closed to these sessions: use `gh api` REST endpoints
  (`gh pr list` and `gh pr view` fail).
- A refused MCP call reaches the hook as PostToolUseFailure, with the text in
  `error`. Hooks load when Claude Code starts, so a session started before
  `.claude/settings.json` existed has no session log.
- The client keeps tool descriptions from before a reconnect; the server's are
  current.
- `inspect` puts two spaces between the line number and the code; they are not
  indentation in `old`.
- Writing Java through a Python script: use raw strings, or a backslash before a
  newline joins the lines. The Edit tool avoids it.
- Check SDK and library APIs with `javap` against the jars, not from memory.
- JDT's `setEnvironment` needs one encoding per source path entry.
- `bin/jrefactor` rebuilds its jar only for a commit newer than the jar; probing
  uncommitted server code takes a temporary commit (roadmap #79).
- To test a server change from Claude Code without registering it, write a
  `--mcp-config` JSON in the scratchpad and run `claude -p "<prompt>" --model
  haiku --strict-mcp-config --mcp-config <file> --allowedTools mcp__<name>__<tool>
  < /dev/null`. Put the prompt first (`--allowedTools` swallows what follows it),
  and redirect stdin, or a warning lands in the output.

## Why past choices were made

- JDT Core headless, not JavaLens: JavaLens returns edits for the model to apply,
  and this project applies them itself, as gorefactor does. Details in
  `docs/design.md`.
- The JDT spike (`spikes/jdt-headless/`) showed bindings resolve with no
  workspace and no OSGi start, against a source path and a classpath.

## Build environment

- The sandbox Maven proxy fix is in `CLAUDE.md`; the port once went stale
  mid-session, so regenerate `~/.m2/settings.xml` before every Maven run.
- Maven Central answered 429 (rate limit) once; the rerun passed.
- The Maven Wrapper was the user's choice over apt, a tarball or Docker, when
  their machine had no Maven. That machine had only JRE packages, which fails as
  "release version 21 not supported"; the fix is `openjdk-21-jdk-headless`. Its
  default `java` is 25, so set `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.
- CI skips the build for docs-only changes. Dependabot opens weekly PRs for
  Maven and Actions.
