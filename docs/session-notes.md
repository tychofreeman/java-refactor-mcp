# Session notes

What sessions working on this project have learned that the code, the README and
the other docs do not record: the user's preferences, why past choices were made,
and where the work stands. Every session reads this (CLAUDE.md imports it). When
you learn something a later session would need, add it here in the same PR as the
work, and correct or remove what has become wrong. Keep it short: facts, with the
reason, not a diary. The roadmap is in `docs/design.md`.

## Working with the user

- One item per branch and PR. Adopted from gorefactor on 2026-11-02, after the
  first items had been committed straight to `main`.
- Claude merges its own PRs once CI is green and they are mergeable (the user,
  2026-11-02); use merge commits, as earlier PRs did.
- Stacked PRs: retarget the upper PR to `main` *before* merging the lower one
  with `--delete-branch`. Deleting the base branch first closed #4, and a closed
  PR whose base is gone cannot be reopened or retargeted until the branch is
  pushed back (2026-11-02).
- Development standards follow gorefactor's (`CLAUDE.md` there, and its
  `docs/how-this-project-was-built.md`). The user asked for the same standards
  here (2026-11-02), including the same goal: about 95% of coding through the
  tools, judged by token cost against editing by hand.
- "What's next?" runs the `whats-next` skill, ported from gorefactor on
  2026-11-02. In gorefactor the user chose a record per item in
  `docs/sessions/` so they can judge whether a session used the tools or worked
  around them. The session log (roadmap #18, 2026-11-03) is the measured part:
  `bin/session-log` and `bin/session-report`.

## Where the work stands

- `TextEdit` is the one replace-back-to-front edit (roadmap order 32, 2026-11-03), used by `Rename`, `MoveType` and `SignatureChange`. `Declarations.plan` and `Edits.replaceText` keep their own shape.
- `Extraction.apply` is split into `Plan`, `callOf` and `methodText` (roadmap order 34, 2026-11-03); behavior is the extract goldens, unchanged.
- `Extraction.Plan.declaredInside` is the set of variables declared inside the selection (roadmap order 42, 2026-11-03). `Stubs`' `implicit` name matches its use. A class this call created gets no default-constructor stub: the user chose that (2026-11-03), and `Stubs` skips it through `created`.
- `Stubs.find` declares a stub for a call that fits no overload (`IProblem.ParameterMismatch`, roadmap order 48, 2026-11-03): an overload typed from the call. A wrong call to an existing method now gets a stub overload, not a refusal.
- The two `ancestor` helpers are named by where they start (roadmap order 29, 2026-11-04):
  `Stubs.ancestorOrSelf` (from the node itself) and `Extract.ancestorAbove` (from its
  parent). The names were chosen by the session, not asked; `rename` moved the calls.
- The server loads its classpath from the same cache the command line keeps
  (order 63, 2026-11-04, `docs/sessions/server-classpath-cache.md`): `Cli.classpathCache`
  names the file, `Server.load` uses it. A second start on this repo takes 1.6 s, not 4.7 s.
- `Names.typeLabel` is the one `Outer.Inner` label (roadmap order 36, 2026-11-03). `Stubs.typeName`
  and `Extract.typeName` still differ on wildcards: `Stubs` erases them, `Extract` keeps them.
  Stub type variables (decided 2026-11-04): written by name when the target type declares
  them, erased otherwise. Writing every type variable by name refuses a stub whose
  caller's `T` is not in scope in the target (`docs/sessions/stubs-type-variables.md`).
- `Edits.gated` fixes imports only for a file under a source root (roadmap order 47, 2026-11-03):
  `SourceProject.packageOf` is null elsewhere, and the gate counts errors only under a source root.
- `Rename.gate` calls `Gate.applyOrRefuse` (roadmap order 33, 2026-11-03): a rename
  that adds compile errors is refused with "would add", as every other edit says.
  `MoveType` says "would add" through the same gate (`docs/sessions/movetype-gate.md`).
- `Journal.put` and `Journal.restore` are the one writer of project files (roadmap
  order 40, 2026-11-03). `Gate` calls them; `Gate.put` and `Gate.restore` are gone.
- Engine errors name where they failed: `Server.answer` puts the first three frames
  in the text and the trace on stderr; a failed undo is added to the failure as
  suppressed, not thrown in its place (`Tools.abort`, roadmap order 35, 2026-11-03).
- `rename` refuses a column below 1 (roadmap order 37, 2026-11-03): `Tools` passes
  the column as given (null when absent), and `Rename.run` takes an `Integer`.
- The end-of-batch check is `Gate.checkedAtEnd` (roadmap order 24, 2026-11-03):
  it counts, runs the body deferred, declares stubs, then refuses and restores or
  journals. `Batch` calls it; `Gate.refusal` is the one refusal wording for both.
- `Gate.deferred` restores the flag it found, so a nested deferred call (a batch
  inside a batch) leaves the outer call deferred (roadmap order 41, 2026-11-03).
- Order 32 (edit pipeline) is closed as partly done (2026-11-03, the user's choice
  from the options, `docs/sessions/edit-pipeline-close.md`). Its remaining part, the
  per-file import fix and gate in `SignatureChange` and `MoveType`, was closed on
  2026-11-04 as not worth merging (`docs/sessions/import-fix-close.md`).
- `Edits.gated` is the one place a single-file edit fixes imports and passes the
  gate (roadmap order 32, 2026-11-03). `Declarations` (3), `Extraction` and
  `Edits.write` use it; `Signatures` keeps its per-file loop. Add new single-file
  edits through `gated`.
- `Jdt` owns every parser (review item 28, 2026-11-03): `Jdt.parseMembers` parses
  class-body members for `Declarations`, and the compiler options come from one
  function. `grep ASTParser` finds `Jdt` only; keep it that way.
- `Extraction`, the nested class of `Extract`, is its own file (roadmap order 25,
  2026-11-03, `docs/sessions/split-extract.md`). Its helpers in `Extract` are
  package-private for it. `Signatures`, `Declarations` and `Tools` are still whole;
  split them when a change needs it.
- Signature tools parse only the files that can hold the method (roadmap
  order 44, 2026-11-03): `MethodFamily.of` (was `Signatures.Family.of`) reads every file's text and parses
  only those containing the method's simple name. A family member and a call of
  one share that name, so nothing is missed; `add_parameter` on Commons Math went
  from 6.5 s to 2.9 s. The summary's option (reuse the gate's parse across calls)
  was not taken: it keeps every AST of the project between calls.
- One line helper (roadmap #27, 2026-11-03): `Lines` holds `number` (1-based line of an
  offset), `start`, `end`, `range` ("line N" or "lines A-B") and `list` ("line N" or
  "lines 4, 7, 9"). The duplicates in `Declarations`, `Imports`, `Edits`, `Tools`, `Rename`
  and `Extract` now call it. `Inspect` and `SessionReport` never had a copy; the review
  row said they did. Batch's "line N of rel" and Rename's "line N, column" refusals are
  separate wordings and were left as they were.
- Built: the type-check gate and `rename`. Layers: `SourceProject` (root and
  classpath), `Jdt` (headless parse), `TypeCheck` (project-wide compile-error
  counts before and after, the gate every refactoring uses), `Refusal`,
  `Positions` (1-based to offset), `Rename`.
- The MCP server runs (roadmap #5, 2026-11-02): `Server` over STDIO, one
  project root per process, one tool, `inspect`. Claude Code lists and calls it
  (`claude -p --mcp-config` probe with Haiku, $0.0008).
- `rename_local` is a tool too (roadmap #6, 2026-11-02), addressed by `name` and
  `line` as gorefactor's `rename` is; `column` only for two meanings on one line.
  A Haiku probe renamed a constructor parameter through it with no column.
- Sessions here have the tools (roadmap #13, 2026-11-02): `.mcp.json` runs
  `bin/jrefactor-mcp`, which rebuilds a stale jar (about 4 s, output to stderr)
  and serves this checkout. After merging a server change, check out `main`
  and reconnect the server with `/mcp` (the user asked, 2026-11-02, why a
  restart was needed; it is not: a reconnect starts a new server process).
- The server exits when stdin ends, but only after it is quiet: the SDK closes
  the session as soon as it reads the end of input and drops requests it has
  read but not run, so the read that finds the end waits until no tool call is
  running and nothing was written for a second. Exiting at once lost every
  reply of a piped script; never exiting leaves a JVM behind a crashed client.
- Maven projects load from their build (roadmap #7, 2026-11-02): source roots
  `src/main/java` and `src/test/java`, and the test classpath from
  `dependency:build-classpath`. This repo now type-checks with zero errors in
  the gate (`SourceProjectTest.thisRepoTypeChecksClean`); loading it costs a
  Maven run, about 3 s, at each server start. JDT's `setEnvironment` needs one
  encoding per source path entry, or it throws `IllegalArgumentException`.
- The first edit tools exist (roadmap #19, 2026-11-02): `create_file`,
  `replace_text`, `add_declaration` (replaces "not implemented" stubs),
  `replace_declaration`. Tool definitions live in `Tools.ALL`; `GoldenTest` calls
  them the same way the server does, with `<arg>.txt` files for long arguments.
- Edits fix imports (roadmap #20, 2026-11-02): `Imports.fix` runs before the
  gate in `replace_text`, `add_declaration` and `replace_declaration`. The class
  index (JDK through `jrt:/`, classpath jars) is built once per server and
  classpath, the sources each call; a server start plus two import-fixing edits
  took 2.2 s.
- The server runs one tool call at a time (`Server.ONE_AT_A_TIME`, 2026-11-02).
  Before that, two edits sent together ran at once and the second undid the
  first (found by a piped probe; `ServerTest.editsSentTogetherBothApply`).
- `batch` and `undo` exist (roadmap #8, 2026-11-02). Every write goes through
  `Gate` (`apply`, or `write` for non-Java) and is recorded by `Journal`; a
  top-level call is `Tools.call`, which begins and commits the journal entry.
  A Haiku probe chose `batch` with check `"end"` for a signature change and its
  caller unprompted (6 turns, $0.004).
- Edits import names that resolve only once others do (2026-11-02): a lambda
  is not type-checked while the type of the call around it is unresolved, so
  `Imports.fix` parses again with the imports found so far until no more turn
  up (`imports_in_lambda`).
- Edits declare stubs (roadmap #21, 2026-11-02): `Stubs.declare` runs inside
  `Gate.apply` and `Batch`'s end check, before a refusal, for methods,
  constructors and classes of the project that nothing declares. The user chose
  automatic stubs only, over a `create_from_usage` tool alone or both: a tool
  waits for evidence that inference picks the wrong place. A member the edit
  removed is not declared again (deleting a used method is still refused). A
  Haiku probe wrote a test first in one `add_declaration` and got `int total()`
  from `total() == 0` (8 turns, $0.0055). The import index leaves out libraries'
  `.internal.` packages (2026-11-02): JDT's internal compiler AST made most
  `org.eclipse.jdt.core.dom` names ambiguous, and a JDK class in an unexported
  `jdk.javadoc.internal` package was imported for a name nothing declared.
- Signature changes exist (roadmap #22, 2026-11-02): `add_parameter`,
  `remove_parameter`, `move_parameter` in `Signatures`. The family is found by
  `IMethodBinding.overrides` against each declaration's supertypes, in each
  file's own parse, joined across files by binding key
  (`getMethodDeclaration().getKey()`). A Haiku probe added a parameter to an
  interface method, two implementations and two calls in one call (10 turns,
  $0.004).
- Extract method exists (roadmap #23, 2026-11-03): `extract_method` in
  `Extract`, Java's name for `extract_function`. The user chose refusing two
  or more results over returning a record, and static exactly when the
  enclosing member is over "whenever it can be". The tool was registered first
  with a handler class that did not exist, and #19's stubs declared it. A
  Haiku probe did an extraction in one call (6 turns, $0.004).
- Results audit (roadmap #24, 2026-11-03): no edit result repeats the input or
  a diff; they report names and line ranges, 31-241 bytes in the golden
  cases. Nothing was changed. The largest is a stub list, which grows with
  the stubs declared (a follow-up in `docs/sessions/results-without-echo.md`).
  The session log recorded nothing for this session: the hook loads only in
  a session started after `.claude/settings.json` existed.
- Rename across files (roadmap #9, 2026-11-03): `rename` (class `Rename`)
  replaced `rename_local`, the user's choice over a second tool or widening
  the old one. Fields, methods (with their override family, from
  `Signatures.family`) and types (with their file; `Gate.apply` deletes a
  file whose text is null) are renamed in every file, Javadoc references
  included. A name with two meanings on the line is refused, and `name`
  picks one (`Counter.count`, `local count`) or column does: the user's
  choice over "the local wins", which renamed the parameter when the field
  was meant. A Haiku probe started from `this.price = price`, was refused,
  and passed `Item.price` (5 turns, $0.0037). `RenameLocal` became `Rename`
  through the new type rename, sent over stdin to the branch's jar.
- Gate cost (roadmap #10, 2026-11-03): parsing file by file, each with a
  fresh JDT environment, cost 21.8 s per edit on Commons Math (990 files).
  `Jdt.parseAll` (`ASTParser.createASTs`) parses a set in one environment;
  `TypeCheck.errors` reuses the last count while every file's text is the
  same. One edit there is now about 2.1 s, `add_parameter` 4.1 s (was 15.0),
  a field rename in 10 files 2.4 s. Measure with the opt-in `GateCostTest`:
  `-Djrefactor.cost.root=<dir>`, and `-Djrefactor.cost.call='{"tool": ...,
  "args": {...}}'` for one call and its undo. The projects were Maven Central
  sources jars unpacked into the scratchpad (JDK-only, so no build runs).
- Golden cases check undo and the cache (roadmap #14, 2026-11-03): every
  write case is undone by `undo` and must return to `in/`, and the cached type
  count must equal `TypeCheck.uncached`. All 51 write cases passed on the first
  run, so no journaling gap exists in the golden set; the check guards future ones.
- `create_file` fixes imports (roadmap #35, 2026-11-03): a new Java file goes through
  `Edits.write` like the other edits, so `Imports.fix` runs before the gate and
  `List<String>` gets its import, not a project stub `List`. The result gains
  "; imported ..." when it imports a name (`create_file_java_imports`).
- Overload names split at top-level commas only (roadmap #36, 2026-11-03):
  `Declarations.topLevelCommas`, so `name=A.b(Map<String, Integer>)` finds the
  overload the refusal listed. Labels stay without spaces (`Map<String,Integer>`),
  as members are labelled. Golden: `replace_declaration_generic_overload`.
- Stub import picks are per file (roadmap #37, 2026-11-03): `Stubs.Round` keeps
  `wantedUses` per target file, filled from the names each stub's handler used
  (`uses` is cleared at each handler's start). Before, every file got every
  stub's picks, so a shared simple name (`java.awt.List`, `java.util.List`)
  took the last one sorted. Golden: `stubs_uses_per_file`.
- A failed Maven classpath is kept and named (roadmap #38, 2026-11-03): the user
  chose "keep serving, name the cause" over refusing to start. `SourceProject`
  has a `classpathProblem`; `Gate.applyOrRefuse` adds the reason to a refusal
  that says a name "cannot be resolved". Test: `GateTest`.
- File refusals are one wording (roadmap #26, 2026-11-03): `SourceProject.javaFile`
  (not a .java file; missing, with the Java files in its folder) and
  `existingFile` (replace_text, any file). `Inspect` keeps "No Java file" (its
  wording is in a golden and two tests). `Signatures` now refuses a non-Java
  file by name, not as "not a Java file under a source root".
- No static mutable state (roadmap #33 and #43, 2026-11-03): `Session` holds, per
  `SourceProject`, the undo history and open call (`Journal`), the deferred flag
  (`Gate.deferred`), the library index (`Imports`) and the last type check
  (`TypeCheck`). `Server.Quiet` holds the call counter, the one-at-a-time lock and
  the last output, per started server. Two project objects over one folder share
  no undo history (`JournalTest.aProjectOpenedAgainHasNoHistory`).
- Call costs on Commons Math 3.6.1 (roadmap #44, 2026-11-03, `docs/sessions/measure-call-cost.md`):
  one whole-project parse is about 3.5 s; rename, replace_text and create_file each
  make one (3.2-3.5 s); add_parameter makes two (7.0 s), because `MethodFamily.of`
  parses every file before the gate. `GateCostTest.timesACall` prints the parse count.
- Import and stub costs on Commons Math (roadmap #44, 2026-11-03,
  `docs/sessions/measure-imports-stubs.md`): a plain `add_declaration` 0.27 s;
  one needing a JDK import 0.96 s; one declaring a stub 0.47 s with a second
  parse.
- A deleted member that an unqualified call in a nested class still uses is refused,
  not declared back (2026-11-04, `docs/sessions/stub-after-delete.md`): `Stubs.removedAround`
  checks the enclosing types too. The removal check also reads the supertypes of the
  owner and of each enclosing type, each in its own file (2026-11-04,
  `docs/sessions/stub-after-delete-inherited.md`): an inherited method deleted from a
  superclass in another file is refused. A sibling nested class is not covered (not reproduced).
- A stub that is the expression of an enhanced `for` is typed `List<` the loop
  variable's type `>` (2026-11-04, `docs/sessions/stub-enhanced-for.md`). A primitive
  loop variable is boxed (`List<Integer>`, 2026-11-04, `docs/sessions/stub-enhanced-for-primitive.md`);
  `List<int>` as a stub's text does not parse, and the refusal then blames the caller's content.
- A lambda argument to an undefined method gets a functional interface by its
  arity (`Supplier`, `Function`, `BiFunction`; the user's choice over refusing, 2026-11-04,
  `docs/sessions/stub-lambda-argument.md`). It is a guess: a `Runnable` parameter or a body that
  uses a parameter's type is still refused.
- Undo is not slow (2026-11-04, `docs/sessions/measure-undo.md`): `Journal.undo`
  takes under 1 ms. The 2.7-2.9 s once recorded was `GateCostTest`'s own uncached
  check, now timed apart. Measure undo with the harness, not from its old figures.
- Stubs travel in the call's result (roadmap #30, 2026-11-03): `Tools.call`
  returns an `Outcome` (text and `Stubs.Declared`; its own file since 2026-11-04, moved
  from `Tools`, roadmap order 46); `render()` gives the text
  the model reads, with the stubs named after it. `Stubs.said()` and its
  `ThreadLocal` are gone. Golden `stubs_*` text is unchanged.
- The session report counts by `Tools.Def.kind` (roadmap #45, 2026-11-03):
  a new tool needs only its `Kind`, not a copy in `SessionReport`. Quoted
  text and heredoc bodies are not read as redirects, and a shell write
  counts lines of Java only when it writes a `.java` file. Records written
  before this may count some reads as hand writes, and Markdown as Java.
- Edits import static methods (2026-11-03, from two friction entries):
  `Imports.fix` imports an unresolved unqualified call statically from the
  one class the file already has a single static import from with that
  static method, so stubs no longer declare `assertFalse` in a test class.
  Several such classes, or none, still leave it to the stubs.
- `move_type` exists (roadmap #52, 2026-11-03): `MoveType` moves a static
  nested type to its own file in the same package, every reference
  following, the outer type's statics qualified and private members used
  across the boundary widened (named in the result). The user chose a new
  tool over an option on `rename` or `replace_declaration`, and widening
  over refusing. A trial move of `Signatures.Family` (327 lines) through the
  branch's jar passed the gate and every test; it was reverted, since the
  split of `Signatures` (order 25) is its own item.
- The command line exists (roadmap #53, 2026-11-03): `bin/jrefactor` runs
  `Cli`, one tool call per process, for sessions where the MCP server cannot
  run (the user's other session, in a container, made orders 24-44's edits by
  hand). `Journal.save` and `load` keep the undo stack between runs in the
  user's cache, beside the classpath (`MavenBuild.classpath(root, cache)`,
  keyed by `pom.xml`'s hash) and the last count (`TypeCheck.save`/`load`,
  each file's hash). On this repo a read takes 0.3 s and an edit 2.4 s (they
  were 9.4 s and 4.7 s): `bin/jrefactor` had rebuilt the jar after each of
  its own edits to `src/main`, so it now rebuilds only for a commit newer
  than the jar.
- `Signatures` is split (order 25, 2026-11-03): `MethodFamily` (the family
  search, `MethodFamily.of`) and `SignatureChange` (the edits of one change)
  are top-level, moved by `move_type` and renamed by `rename`; `Signatures`
  keeps the three tools' entry points, `Source`, `Decl`, `Call` and the
  helpers the three share.
- The check after an edit is narrowed (roadmap #56, 2026-11-03):
  `TypeCheck.Counted` keeps each file's `Headers`, and `errors` parses only
  the changed files and those naming a name whose header changed. Commons
  Math: one edit 64 ms (was 2.1 s), `rename` 1.3 s, `add_parameter` 1.9 s.
  Soundness rests on `GoldenTest`'s comparison with `uncached` at every
  step, `GateCostTest`'s on a real project, and the opt-in
  `NarrowCheckStressTest` (random declaration edits; a killed run is put back
  by the next one from `.jrefactor-stress-snapshot.json`, `StressSnapshot`). Following names
  alone missed edits seen through inheritance; the rule now follows
  subtypes and headers that name a changed type. A cache file of another
  `version` is ignored.
- A rule in the gate that skips work (the narrowed check, #56) is checked
  by a stress run on a real project before it is merged, not after: #56's
  first rule missed three of 60 random edits on Commons Math, all through
  inheritance, though every example test passed (2026-11-03).
- `RefactoringStressTest` (opt-in, `-Djrefactor.stress.root`, `.calls`,
  `.seed`, `.counts`) calls rename, move_type and the signature and extract
  tools on random targets of a real project. No failures in 160 calls on
  Commons Lang and Math (2026-11-03). It prints refusals that the gate made
  ("stress gated:") in full: those are code a tool wrote that did not
  compile, a precondition the tool lacks.
- The signature tools change the lambdas that implement the method (#57,
  2026-11-04; they refused them from #72): a lambda's parameter list is
  written again. They look for lambdas in every file with `->` when the
  family has an abstract interface method, since a lambda need not name the
  method. Before, `move_parameter` on
  such a method compiled with the lambdas' parameters swapped (seen on
  Commons Lang's `FailableObjIntConsumer.accept`), which neither the gate
  nor the stress test can see.
- `move_type` qualifies what the outer type inherits (2026-11-03): a member
  type or static of the outer type's supertype, used unqualified in the
  moved type, gets the declaring type's name and keeps its import. Before,
  the name was left unqualified and the stubs declared an empty class for
  it, which the gate passed (friction log: stubs should not run for a
  refactoring's own output).
- Stubs are declared only for edits (2026-11-03): `Tools.call` sets
  `Session.editing` for a call of `Kind.EDIT`, and a batch keeps it once a
  step was an edit; `Stubs.declare` declares nothing otherwise. A
  refactoring that leaves a name unresolved is refused: before, `move_type`
  left an inherited `Helper` unqualified and the stubs declared an empty
  class for it, which the gate passed.
- The next ten items are split between sessions (the user, 2026-11-03): the
  local session takes #57-#61 (the refactorings and their stress tests), the
  cloud session #62, #63, #64, #34 and the rest of #32 (pipeline, server,
  structure). Each roadmap row's status names its session; see "Split
  between sessions" in `docs/design.md`. Take another session's item only
  when the user says so.
- A stub goes in the source root of the file that uses it (2026-11-04,
  `docs/sessions/stubs-test-tree.md`): a class only a test uses is declared in
  `src/test/java`, not `src/main/java`. Test: `StubsTest`. Goldens have one root,
  so a two-root case is a unit test.
- `replace_declaration` with empty content deletes the file when it removes
  the file's only top-level type (2026-11-04), through the gate and the
  journal; the stubs do not declare a class whose file the edit deleted.
  Before, the file was left with its package line, and removing it took
  `rm` (friction log, twice).
- `pull_up` exists for methods (roadmap #17, 2026-11-04, `docs/sessions/pull-up.md`):
  a method moves to the direct superclass, or to the direct supertype named by `to`;
  the gate refuses a move that leaves a name unresolved (no stubs, since a refactoring
  has `Session.editing` false). `push_down` (2026-11-04, `docs/sessions/push-down.md`) moves
  a method to every direct subclass, and refuses an override, since moving it would change what
  the class runs. Both share `MemberMove`, which also lifts a field (2026-11-04, `docs/sessions/fields-pull-push.md`) and picks an overload by its parameter list, as `replace_declaration` does (`docs/sessions/overloads-pull-push.md`). Signatures are compared by binding, not by name. Originally added at the
  user's request (2026-11-03), with a note that gorefactor and the TypeScript port may want
  the same.
- The session log (roadmap #18, 2026-11-03): `SessionLog` is the hook, run
  from the jar by `bin/session-log` for PreToolUse, PostToolUse and
  PostToolUseFailure; `SessionReport` reads the log. A refused MCP call
  arrives as PostToolUseFailure with the text in `error`, not as
  PostToolUse (found with a probe hook that appended its input); gorefactor
  logs only PostToolUse, which works there because its refusals are not
  error results. The hook costs about 0.2 s per event (two per call). It
  logs nothing until the jar is built; hooks load when Claude Code starts,
  so a session started before `.claude/settings.json` existed has no log.
- `gh pr checks` right after `gh pr create` can say "no checks reported"
  before CI registers, and `--watch` then returns at once: #22 was merged
  that way before its run (which passed). Wait for the run to appear. A PR
  that conflicts with `main` gets no run at all (#29, while other sessions
  merged #25-#28): check `gh pr view N --json mergeable` when none appears.
- The client keeps tool descriptions from before a restart in its listing (seen
  twice: after #19 and #20); the server's are current. Not this server's bug.
- Two `replace_text` "old is not in" refusals (#20, #8) were the model reading
  `inspect`'s two-space separator as indentation.
- Writing Java through a Python script: use raw strings. In a plain Python
  string a backslash before a newline joins the lines, which is how
  `rename_local`'s description got its runs of spaces. The Edit tool avoids it.
- The user asked for roadmap positions 1-8 in order, one PR each (2026-11-02):
  #7, #19, #20, #8, #21, #22, #23, #18.
- The roadmap's open items are ordered by gorefactor's evidence (the user asked,
  2026-11-02, after items had been ordered only by what unblocks what): source
  roots and classpath (#7), then the first edit tools, imports, undo and batch,
  stubs, signature changes. The reasons are under "Why this order" in
  `docs/design.md`.
- The SDK (2.1.0) was checked with `javap` against the jars, not from memory:
  `Tool.builder()` is deprecated in favor of `Tool.builder(name, mapper, schema)`,
  and the SDK validates arguments against the input schema itself.
- To test a server change from Claude Code without registering it, write a
  `--mcp-config` JSON in the scratchpad and run `claude -p "<prompt>" --model
  haiku --strict-mcp-config --mcp-config <file> --allowedTools mcp__<name>__<tool>`.
  Put the prompt first: `--allowedTools` takes several values and swallows it.
  Redirect stdin (`< /dev/null`): otherwise it waits 3 s and prints a warning
  into the output, which then does not parse as JSON (the cause of two "not
  JSON" probe outputs, in #6 and #19).

- Tool count limit 50 (roadmap order 15, 2026-11-04): `ToolsTest` holds it. The user
  raised it from the implied budget to add ten higher-level tools that replace
  `replace_text` in the composites of the Fowler, Go and IntelliJ comparison.
  Those ten are not built yet; each is its own PR, in the order the user gave.

## Why past choices were made

- JDT Core headless, not JavaLens: JavaLens returns edits for the model to apply,
  and this project applies them itself, as gorefactor does. Details in
  `docs/design.md`.
- The JDT spike (`spikes/jdt-headless/`) showed bindings resolve with no
  workspace and no OSGi start, against a source path and a classpath.

## Build environment

- Sandbox Maven needs `~/.m2/settings.xml` with an active `https` proxy at the
  host and port in `$HTTPS_PROXY`; without it Maven fails with "Name or service
  not known" for `repo.maven.apache.org`, even when `curl` reaches it. The port
  changes between sessions and once went stale mid-session, so regenerate the
  file before every Maven run. No mirror is needed.
- Maven Central answered 429 (rate limit) once; the rerun passed.
- Maven Wrapper added 2026-11-02 (the user's choice over apt, a tarball in
  `~/.local`, or Docker) when the user's own machine had no Maven: one pinned
  Maven everywhere, CI included. That machine also had only JRE packages
  (`openjdk-21-jre`), which fails as "release version 21 not supported"; the fix
  is `openjdk-21-jdk-headless`. Its default `java` is 25, so set
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.
- CI: `./mvnw verify` on JDK 21 plus a `spotless:check` job; runs are skipped for
  docs-only changes. Dependabot opens weekly PRs for Maven and Actions. As of
  2026-11-02: JUnit 6.1.3, `actions/checkout@v7`, `actions/setup-java@v6`. The
  first runs on `main` passed (2026-11-02).
