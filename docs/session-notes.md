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
  commits, then delete the PR's branch (78 merged branches had piled up by
  2026-11-04; deleting them in bulk from a session was refused as destructive,
  so the user deletes them).
- Releases (2026-11-05, the user): every push to `main` that changes code and
  passes CI is released by the `release` job of `ci.yml`, with the jar attached.
  Label a PR `minor` (a new tool) or `major` to bump that part; otherwise the
  patch. There is no version to raise by hand: the jar's manifest carries it
  (`Server.version()`).
- Stacked PRs: retarget the upper PR to `main` *before* merging the lower one
  with `--delete-branch`. Deleting the base branch first closed #4, and a closed
  PR whose base is gone cannot be reopened or retargeted.
- Development standards follow gorefactor's (`CLAUDE.md` there, and its
  `docs/how-this-project-was-built.md`), with the same goal: about 95% of coding
  through the tools, judged by token cost against editing by hand.
- The read and write guards (2026-11-05, the user; roadmap #15 and #104, as
  gorefactor's `cmd/read-guard` and `cmd/write-guard`): `bin/read-guard` and
  `bin/write-guard` run `Guard` from the jar and deny reading a `.java` file
  other than through `inspect`, and changing one other than through the tools,
  outside `resources/`. The write guard is an experiment. With both on, a change
  no tool makes is logged and asked of the user, not made by hand.
  A session started before they existed has neither (hooks load at start).
- The README (2026-11-05, the user asked for a review for programmers who let
  models do most of the work): what you get today and what is not proven, the
  tools, requirements (a single-module Maven project), install, Claude Code,
  using the tools well, troubleshooting, the CLI; contributor links at the end.
  `examples/CLAUDE.md` is `eval-tasks/claude-md/mcp.md`, the instructions the
  A/B runs measured, with a general test command: when a run finds better
  instructions, update both. Keep the README's evidence sentence and tool list
  current with `docs/design.md` and `Tools.ALL`.
- "What's next?" runs the `whats-next` skill. Its record per item lets the user
  judge whether a session used the tools or worked around them; the session log
  (`bin/session-log`, `bin/session-report`) is the measured part.
- Before fixing a roadmap row, check `main` and the open branches for the same
  row: two sessions once fixed #67 at once, and one PR (#118) was closed.
- gorefactor's `docs/parity.md` (Go, TypeScript and Java at parity) is the source of roadmap
  #119-#130 (2026-11-06, the user asked for it to be merged with this repo's next items); the
  merged order is "Next, in order" in `docs/design.md`. When an item there is done, mark it
  in parity.md too (gorefactor's repo, its own PR).
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
- Stubs: declared automatically by edits, no `create_from_usage` tool. A constructor stub whose
  arguments each fit one field of the class assigns them, not throws (the user, 2026-11-07:
  `Stubs.Round.setting`; the options are in `docs/design.md`). A static field read
  `T.NAME` gets `static <type> NAME; // not implemented` (#107, 2026-11-06): a field cannot
  throw, so the comment is what `add_declaration` knows it by (`Stubs.FIELD_MARK`). A bare
  name nothing declares gets the same, in the type around it (`Stubs.Round.bareField`; the user,
  2026-11-08, any lowercase name over instance code only and over only names typed by their use:
  a typo of a local becomes a field stub, which the result names). A class
  the same call created gets no default-constructor stub. A lambda argument to
  an undefined method gets a functional interface by its arity (`Supplier`,
  `Function`, `BiFunction`), a guess. Stub type variables are written by name
  when the target type declares them, erased otherwise.
- `rename` is one tool for every kind of name. When a line has two meanings it
  refuses, and `name` picks one (`Counter.count`, `local count`); not "the local
  wins".
- `extract_method` returns several results in a record named after the method
  (`BothResult`), declared after it (the user, 2026-11-04, reversing the 2026-11-03
  choice to refuse them, over an out-parameter holder); the new method is static
  exactly when the enclosing member is.
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
- The missing refactorings are roadmap #80-#91 (2026-11-04), in the user's
  order, after #79: a type to another package and `move_file`, a signature
  type change, safe delete, widening, extract interface/superclass, introduce
  constant/field/parameter, lambda/anonymous/method reference, record
  conversion, extract class and parameter object, instance/static in place,
  the edit primitives. `extract_method` with several results would reverse
  the choice above: ask before building it. Left off: renaming a package,
  encapsulating a collection.
- Ambiguous imports (2026-11-05, `docs/design.md`, roadmap #105): an edit takes the class
  the file imported before the call or batch (`Journal.written`), else the only one in a package
  it imports from, else (#131, 2026-11-06) the only one in the project's own sources, and names it;
  the package's other files' imports come second, before the packages rule (2026-11-06,
  `docs/sessions/package-import-evidence.md`: a new file has no imports of its own to go by);
  otherwise one refusal lists every ambiguous name, the project's class suggested first.
- Parity with Go and TypeScript (2026-11-05, row 112, gorefactor's `docs/parity.md`):
  one name per operation across the servers, the more generic one, the others kept as
  aliases. Here that renamed extract_method to extract_function (#120, 2026-11-06: aliases in
  `Tools.ALIASES`, accepted by `batch` and the CLI, not published) and extract_supertype stays;
  move_statement and change_type are already the shared names. Go and TypeScript get `preserve_exceptions`.
  Parity is kept where it makes sense (the user, 2026-11-06): `extract_struct` became `extract_type`
  here first, a name generic enough for the three languages, for Go and TypeScript to follow
  (gorefactor's #597); there, extract_interface became extract_supertype (its #601), and here
  extract_interface is an alias of extract_supertype.
  One `move` (the user, 2026-11-05) replaces move_type, move_member and move_file, the old
  names as aliases (#121, 2026-11-06: `Tools.move` routes by its arguments, as gorefactor's
  `Router`); refusals name `move`, since a model calls only what is published. `rename` of a
  file path renames the file. One `inline` likewise replaces inline_function and
  inline_variable (#122, 2026-11-06: `Tools.inline`, `line` for a local, `Type.method` for a
  static method), and takes inline class when it is built (#128).
- Layout is the tool's, not the model's (the user, 2026-11-07, options in `docs/design.md`):
  `add_declaration` places members by kind (`Placement.place`); `after` and `before` are retired,
  accepted and ignored (`Tools.RETIRED_ARGS`, dropped by `Args.without` before the argument check).
  A new option that would make the model choose a place is against this choice. A tool that adds
  a member to a type places it the same way, through `MemberMove.addTo` (by `Placement.rank`):
  `pull_up`, `push_down`, `move` of a static field and `extract_variable` with `as` do.
- `fold` of an instance method (the user, 2026-11-08, over the same class only and over any
  receiver): matches in instance members of its class and subclasses become `m(...)`, refused
  when the project overrides it (`Fold.overrider`, `Fold.thisOf`).
- The signature tools by line (the user, 2026-11-08, over the declaration's line only): `line` of a
  declaration or a call in place of `name`, `column` for two methods on a line; `ByLine.named`
  turns it into the declaring file and the name, so the tools themselves read `name` as before.
- Order 32 (one edit pipeline) is closed as partly done: `SignatureChange` and
  `MoveType` keep their own per-file import fix and gate.

## Where code goes

- `Jdt` owns every parser; `grep ASTParser` finds `Jdt` only. A unit parsed from a
  file carries its path (`Jdt.fileOf(unit)`), which a check across parses can key by.
- Append to a file (the friction log, a CI job) with `replace_text` `append=true`,
  not a shell redirect.
- A refusal about a column names the columns where the thing does start
  (`Nodes.columns`, #101): models count indentation wrong, and so did a session.
- Offsets and lines: `Nodes.end` (just past a node), `Nodes.within`, `Lines`
  (`number`, `start`, `end`, `offsetOf`, `indentOf`). Nodes of one type:
  `Nodes.all(root, Type.class)`, not a new anonymous `ASTVisitor` (#98).
- `Journal.put` and `Journal.restore` are the one writer of project files; the
  journal also keeps a file's mode (`Journal.mode`, `copyMode`), so undo puts back
  an executable file executable.
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
  `Effects.Kind`. `Effects.mayThrow()` is the syntax-only "may throw";
  `Effects.later(node, root)` and `Effects.sometimes(node, root)` say where code
  runs later, or only sometimes or more than once, than the code that holds it
  (#76; the branches of statements too). `Behavior` asks `changesNow()` of moved code: a lambda
  or anonymous class body runs when it is called, not where it is written (#297). A new tool that moves code into or out of
  a construct asks them, not its own parent walk.
- A refactoring that moves code declares it to `Behavior.check` (a `Move`: the code,
  the root it stays in, where it lands, a label and the edit's verb) with its edits;
  the gate runs the effect, place, copy, exceptional and `NameCheck` rules (#96,
  the user's choice of 2026-11-05). New rules go there, not in a tool. Migrated so
  far: `inline_variable`, `extract_variable`, `move_statement`, `inline_function`
  (each argument; code from outside the root, as an argument is, ran just before it).
  `Behavior.flow` is the gate without `NameCheck`, for a tool that runs it over
  several fragments itself. Per the policy, code that may throw
  leaving a guard or a catching try is refused there; entering one is reported.
- A refactoring reports or refuses a change to exceptional behavior through
  `Exceptional.change`, or keeps it and reports through `Exceptional.report`.
  Every `Kind.REFACTORING` `Def` has `preserve_exceptions` from its constructor.
  Goldens pin the separate part of the result in `exceptional.txt`; a case
  without that file must report nothing.
- A tool's result is an `Outcome`; `render()` gives the text the model reads,
  with the stubs named after it.
- Stubs are declared only for edits (`Session.editing`, set for `Kind.EDIT`); a
  refactoring that leaves a name unresolved is refused. A class stub goes in the main
  (first) source root, the using file's package, so a test written first declares its subject
  in `src/main/java`; a name that reads as a test double or helper (`Stubs.TEST_DOUBLE`:
  `Fake*`, `*Helper`, ...) stays in the test's root (#113, the user's choice, 2026-11-06, over
  always the main root and over a move between roots).
- The session report counts by `Tools.Def.kind`, so a new tool needs only its
  `Kind`.
- A new tool's arguments are a `List<Arg>` (`Arg.FILE`, `Arg.IMPORTS`,
  `Arg.string(...).required()`, ...), not JSON (#97). To check that a rewrite of
  `Tools` keeps every schema, diff two runs of `ToolSchemasTest`
  (`-Djrefactor.schemas.out=<file>`).
- `Session.refusedBatch` keeps the last refused batch for `batch` `retry` (2026-11-06); a batch
  that passes clears it.
- The server runs one tool call at a time (`Server.ONE_AT_A_TIME`): two edits
  run at once undid each other. It exits when stdin ends only after it is quiet,
  since the SDK drops requests it has read but not run.

- `move_type` with `package` (#80, 2026-11-04) is `MoveToPackage`: a file's only
  top-level type to another package of its source root; package-private
  declarations a use crosses the new boundary with become public, found by
  binding (`findDeclaringNode` by key) in the file its package and name imply.

- `move_file` (#81, 2026-11-04) is `MoveFile`: each file through `Gate.write` (a null
  text deletes), so a move, copy or delete is one undo. Text only; Java under a
  source root is refused for `move_type`.

- `change_type` with `parameter` or `return_type` (#82, 2026-11-04) is
  `Signatures.changeType`: the family's declarations and typed lambdas, by
  `SignatureChange.replace`; calls are left to the gate.

- `replace_declaration` with empty content is a safe delete (#83, 2026-11-04):
  `SafeDelete.check` names the uses by binding before the gate, and refuses an
  override of a concrete method. The stub goldens that deleted a used member
  (`stub_after_delete_*`) are now refused there, before the stubs run.

- `extract_supertype` (#85, 2026-11-04) is `ExtractSupertype`, composed in one call
  from the gate (the new file), `AddSupertype`, and `PullUp` per member. A member
  `pull_up` or `push_down` removes now takes one blank line beside it with it
  (`MemberMove.removal`); three goldens that pinned the leftover blank line changed.

- `extract_variable` with `as` (#86, 2026-11-04) is `Introduce`: a constant, a field,
  or a parameter through `Signatures.addParameter`; literals and constants only.

- `convert_lambda` (#87, 2026-11-04) is `ConvertLambda`: lambda, method reference and
  anonymous class to one another; the type check catches an ambiguous overload.

- `convert_to_record` (#88, 2026-11-04) is `ConvertToRecord`; a record's generated
  `equals`/`hashCode`/`toString` change behavior, so the class must define them or the
  model must pass `value_semantics=true` (this session's decision).

- `extract_type` (named `extract_struct` until 2026-11-06, the user: "struct" is Go's word; the old
  name is an alias in `Tools.ALIASES`) takes one shape since 2026-11-06 (`name`, `members`, `new_type`, `as`; `new_type` was `struct` until 2026-11-07, still accepted): the case
  follows from what `name` is; old argument names are renamed in `Tools.call` (`ARG_ALIASES`).
- `extract_type` (#89, 2026-11-04) is `ExtractType`: a parameter object, through
  `MethodFamily` and `SignatureChange.replaceRange`; with `fields`, extract class
  (`ExtractType.fields`), for fields only their class uses. On a record, `fields` is
  `ExtractType.components` (#118, 2026-11-05): constructions, accessor calls and field reads
  found by the record's binding key in one `parseAll`; `value_semantics=true` accepts the
  changed `toString` (the user's choice; the options it beat are in the contract).

- Instance to static in place is `move_member` with `to` its own class; static to
  instance is `function_to_method` (`FunctionToMethod`, #90, 2026-11-04).

- `create_file` with `declaration` (#114, 2026-11-05) is `Skeleton`: the header parsed with
  bindings, then stubs for `Skeleton.unimplemented` (abstract methods of the supertypes that
  nothing in the hierarchy implements, by `isSubsignature` within one parse).

- `implement` on `add_declaration` (#123, 2026-11-06) is `Implement`: the target type from a
  binding parse, `Skeleton.unimplemented` for `*`, else the supertypes walked nearest first;
  `Skeleton.stub` and `Skeleton.override` write the members.

- An edit that leaves new final fields unset in a class with no real constructor writes it
  (2026-11-07, the user's choice, options in `docs/design.md`): `FromFields.complete`, called in
  `Edits.gated` on the import-fixed text; its note goes in `Imports.Fixed.notes`, so every edit's
  result says it with no change to the tool. A constructor `add_declaration` adds replaces one of
  its parameter types that only assigns fields (`Declarations.stubFor`), so fields then
  `constructor` in one batch still works (`docs/sessions/ab-constructors.md` found the duplicate).

- `test_for` on `create_file` and `add_declaration` (#115, 2026-11-05) is `TestFor`: the
  target through `Holding`, the framework through `Imports.known`; `add_declaration` passes
  the framework's classes as chosen imports, so the import fix places them.

- An unresolved call in a file that imports from JUnit is tried against the framework's
  assertion class (`Imports.assertions`, #117, 2026-11-05) before stubs: a test class made by
  `declaration` has no static imports for `staticImports` to follow.

- The edit primitives (#91, 2026-11-04) are `Primitives`: `replace_expression`,
  `insert_statements`, `replace_statements`, `add_case`, `add_element`, each an edit
  through `Edits.gated`. `Lines.end` is the offset past the newline.

- `NameCheck` (#77, 2026-11-04) is the general check that moved code keeps its
  meaning, by binding key per identifier, before the edit is written. A new tool that
  moves code should call it (`meanings` of the source fragments, `require` with the
  edits, and the call sites' arguments when the tool rewrites calls); `inline_function`,
  `inline_variable`, `convert_lambda`, `pull_up`, `push_down`, `move_member`,
  `extract_method` and `move_type` do. It skips qualified names, recovered bindings
  and type variables.

## Testing

- A golden case with `skeleton_only=true` runs with `Session.skeletonOnly` on (roadmap #116).
  Under the flag, `Generated` also refuses typed bodies a command writes (2026-11-06): a
  field-assigning constructor, an override that only calls `super` or throws, a statement wrap;
  also in a type sent whole through `replace_declaration`, and in each method that
  `replace_statements`, `replace_expression`, `insert_statements` or `replace_text` adds or changes
  into that shape (`Generated.checkChanged`, 2026-11-07); a member already in it is left alone.
- A golden case with `classpath=junit` in `args.properties` gets the JUnit jars of the test
  JVM (`GoldenTest.junitJars`), for a tool that needs a library to resolve.
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
  seven behavior changes that type-checked (`docs/sessions/stress-new-tools.md`),
  and one more in `fold` (#186). Add them as cases of `ProbeTest`
  (`src/test/resources/probes/<case>`: `in/p/Main.java` printing what it did,
  `args.properties`, `probe.properties` with `expect` applied, refused or changed);
  they run with every build.
  The refusals that followed are conservative, and compare first uses only.
- A/B runs use gorefactor's harness from its checkout, started with JDK 21 first
  (`export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64 PATH=$JAVA_HOME/bin:$PATH`; without
  it every Maven check fails, "release version 21 not supported", 2026-11-05): `go run ./cmd/refactor-ab
  -model sonnet -arms mcp,manual -runs 4 -tasks <this repo>/eval-tasks/warehouse.json
  -claude-md <this repo>/eval-tasks/claude-md -mcp <this repo>/eval-tasks/jrefactor-ab-server
  -out <this repo>/eval-runs/<name>` (about $3.40 for 32 runs). Do not change
  `src/main` during a run: the wrapper rebuilds the jar from this checkout. The
  tools appear as `mcp__gorefactor__*` there. The fixture's checks run Maven
  offline (`-o`), so `~/.m2` must hold JUnit 6.1.3 and the plugins.
- The merged list (one `move`, one `inline`, `extract_function`) was A/B-checked on 2026-11-06
  (`docs/sessions/ab-merged-tools.md`): every run passed, cost as the baselines; the mcp arm alone
  was run, as the manual arm does not see the tool list.
- The constructive task set is `eval-tasks/library.json` (2026-11-06, `docs/sessions/constructive-tasks.md`):
  four tasks an IDE does with `implement`, `constructor`, `surround` and `test_for`.
- The library tasks ran on 2026-11-06 (`docs/sessions/ab-library.md`): the constructive options went
  unused even where the tasks call for them; manual was cheapest and passed every run.
- The written constructors (#272, #273) ran on 2026-11-07 (`docs/sessions/ab-constructors.md`,
  `eval-tasks/library-constructors.json`): #272 replaced the `constructor` call where the model
  left it out, and collided with it where the model sent both (a duplicate constructor); after
  #275 the rerun (`docs/sessions/ab-value-classes-rerun.md`) sent both and passed with no duplicate.
- The constructor stub that sets fields (#273) ran on 2026-11-07 (`docs/sessions/ab-constructor-stub.md`,
  `eval-tasks/library-card.json`, a fixture class with fields and no constructor): it fired in all four
  runs, no run wrote the constructor, and no run named a place for a member; the skeleton arm lost four
  calls a run to a body using a field not yet declared. After #288 (2026-11-09,
  `docs/sessions/ab-card-bare-field.md`): 14.5 turns a run against 19.5; one run still lost two calls,
  as the stub missed a field named as its getter, since fixed.
- The refusal hints were rerun on 2026-11-07 (`docs/sessions/ab-hints.md`, `eval-tasks/library-hints.json`):
  each hint that fired was followed in the next call; `replace_declaration` of a type passes the
  skeleton rule.
- The repeated-construction tasks are `eval-tasks/library-repeat.json` (2026-11-06,
  `docs/sessions/repeated-constructions.md`): models do like constructions in parallel, one
  refusal reaching all; value classes were written as record headers; a test's stubs block
  `declaration`.
- The skeleton arm ran on the library tasks after #255 (2026-11-06, `docs/sessions/ab-library-skeleton.md`):
  8/8, and each refusal naming `constructor`, `surround` or `declaration` was followed by that
  operation; a turn dearer per steer ($0.126 a run against $0.117).
- The skeleton arm was rerun on 2026-11-06 (`docs/sessions/ab-constructive-ops.md`): cheaper
  and less typed, but the constructive options went unused on these tasks.
- The feature tasks are `eval-tasks/shop.json` (gorefactor's `design-*` shop tasks, ported
  2026-11-05, each validated: hidden tests fail on the fixture and pass with a reference
  solution). The skeleton arm is a second run with `-arms mcp` and
  `-mcp <this repo>/eval-tasks/jrefactor-ab-server-skeleton` (`JREFACTOR_SKELETON_ONLY=1`).
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
  indentation in `old`. Since 2026-11-06 `replace_text` matches `old` with the spacing
  within lines ignored (gorefactor's rule, the user's choice replacing #106's uniform shift)
  and moves `new` to the file's indentation, so the trap costs a note in the result, not a
  resend.
- A heredoc ends at the first line equal to its delimiter, also inside the text: a
  README example containing `EOF`, sent through `<<'EOF'`, ran the rest as shell
  commands (a `bin/jrefactor undo` among them; it refused). Pick a delimiter the text
  does not contain (`<<'JAVA'`), for `key_file=-` too. For a batch, put code in
  `@@name` sections after the JSON (`"@name"` in it), not escaped in strings
  (2026-11-07, gorefactor's #606; `docs/sessions/batch-sections.md`). A section
  cannot hold a line `@@name` itself: send such text with `key_file=-`.
- Writing Java through a Python script: use raw strings, or a backslash before a
  newline joins the lines. The Edit tool avoids it.
- Check SDK and library APIs with `javap` against the jars, not from memory.
- JDT's `TypePattern.getPatternVariable()` returns a node without bindings; the parse resolves
  `getPatternVariable2()` (2026-11-06, `TypeSwitch`).
- JDT's `setEnvironment` needs one encoding per source path entry.
- JDT's `SimpleName.isDeclaration()` is false for a record's name (and a constructor's): test
  `t.getName() == n` on the `AbstractTypeDeclaration` too (2026-11-08, `Rename.checkMeanings`).
- Tool descriptions say what a tool does: no refusal lists, nothing every tool shares (that is
  `Server.INSTRUCTIONS`), per CLAUDE.md (the user, 2026-11-06). Paths are said once to be relative
  to the project root, in the instructions; an argument says what file it is (2026-11-07 review,
  `docs/sessions/descriptions-review.md`).
- A tool description is a text block: a line added at another indentation than
  its neighbours makes Spotless re-indent the block, and `ServerTest`'s
  runs-of-spaces check then fails. Write new lines at the block's indentation
  (twice on 2026-11-04, #151 and #153).
- Bindings from two parses (the tool's `Opened` parse and `Holding`'s or
  `parseAll`'s) do not compare: `overrides`, `isSubTypeCompatible` and `isEqualTo`
  return false. Compare keys or qualified names, or take both from one parse (hit
  twice on 2026-11-04: `inline_function`'s override check, `function_to_method`'s).
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
