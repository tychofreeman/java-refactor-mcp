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
- The version a model sees heads the server's instructions, `java-refactor-mcp 0.30.2. ...`
  (`Server.instructions()`, #346, the user's choice of 2026-11-11 over a `version` tool):
  serverInfo carries it too, but a model is not shown that.
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
  instructions, update both. Both changed on 2026-11-10 without a run, from a
  session in a project that used the tools: refactor before the test and
  never while red, `test_for` named, load every schema with one ToolSearch
  (the follow-ups of `ab-shop-tasks` and `ab-warehouse-2`), `inspect` with
  `names` and `pattern` in place of grep, and a tool sequence tried before a
  hand edit, logged in `docs/friction-log.md`. The next A/B run measures them. Keep the README's evidence sentence and tool list
  current with `docs/design.md` and `Tools.ALL`.
- "What's next?" runs the `whats-next` skill. Its record per item lets the user
  judge whether a session used the tools or worked around them; the session log
  (`bin/session-log`, `bin/session-report`) is the measured part.
  Since #168 the report also says what carried each edit step to Java (`build`, `content`, ...)
  and how much Java was typed against names given to `build`: the generators' trend.
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
  Narrowed (2026-11-13, the user, roadmap #173, options in `docs/design.md`): for an integer
  `/` or `%` and a dereference, only a guard that tests the divisor or the receiver keeps it
  (`Guards`); leaving any other guard is reported.
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
- Overloads written alike (`<T> T raise(T)`, `<T extends Throwable> T raise(T)`; #408,
  2026-11-13, the brief's smallest option over a bound syntax and over `line` alone): `name` takes
  their erased parameter types (`raise(Throwable)`), tried after the written ones
  (`Declarations.find`, `erasedTypes`); `Declarations.methodLabel` is the label that names a method
  (erased only when another of its type is written alike): use it, not `MemberMove.label`, for a
  name a tool or a refusal hands back. `MemberMove.member` does not take erased names yet.
- `rename` is one tool for every kind of name. When a line has two meanings it
  refuses, and `name` picks one (`Counter.count`, `local count`); not "the local
  wins".
- `extract_function` with `visibility` (#321, 2026-11-10; the user said yes to an option for
  visibility and for overriding): one option, the override following from the signature, over
  a separate `override` flag; `Extraction.checkOverride` decides when calls of the overridden
  method could reach the new one.
- `extract_method` returns several results in a record named after the method
  (`BothResult`), declared after it (the user, 2026-11-04, reversing the 2026-11-03
  choice to refuse them, over an out-parameter holder); the new method is static
  exactly when the enclosing member is.
- `remove_parameter` with `value` (#315, 2026-11-11, the user, after a before/after example):
  an option, over a `parameter_to_field` tool; `Signatures.removeWithValue` compares each call's
  argument with the field by binding key and the call's receiver.
- Generators (2026-11-13, the user, after reading `docs/sessions/generators-sketch.md`: "go by
  each sketch's recommendations"; the options each beat are in `docs/design.md`, Decisions): a
  `build` argument on `insert_statements`, `replace_statements` and `replace_expression`, not new
  tools, gorefactor's names as unpublished aliases; slice order `replace_parameter`'s query form,
  then call, loop, guard. `replace_parameter` here is `remove_parameter` with `value` an
  expression (roadmap #147; with calls, #148): `Signatures.removeWithExpression`, always a local
  first in the body; `replace_parameter` is its unpublished alias (#160, `value` required). Its effect rules are by position at each call and stricter than the sketch's
  (`docs/design.md`, Decisions): with a call in the value, an argument after it must be fixed, not
  only effect-free.
- `move_statement` with `past_try` (#343, 2026-11-11, the user: option (a), over keeping the
  refusal or only rewording it): the one option that changes behavior on purpose; it skips the
  behavior gate and names the change in the result (`MoveStatement.pastTryNote`).
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
  `pull_up`, `push_down`, `move` of a static field and `extract_variable` with `as` do. It indents
  the member as the type's members (`MemberMove.memberIndent`), each step inside it as the target
  file's step (`MemberMove.reindented`, #316); it had written two spaces. The first member of an
  enum that has constants only goes through `MemberMove.afterConstants` (#141), which writes the
  `;` after the last constant: `Declarations.plan` (so stubs too) and `MemberMove.appendTo` ask it
  before their own end-of-type branch, since an enum's constants are not `bodyDeclarations()`. A field an edit adds
  that an earlier field's initializer reads moves before that field (`FieldOrder`, called in
  `Stubs.declare`, #357), so the repair also sees a reader a later batch step writes.
- `fold` of an instance method (the user, 2026-11-08, over the same class only and over any
  receiver): matches in instance members of its class and subclasses become `m(...)`, refused
  when the project overrides it (`Fold.overrider`, `Fold.thisOf`).
- The signature tools by line (the user, 2026-11-08, over the declaration's line only): `line` of a
  declaration or a call in place of `name`, `column` for two methods on a line; `ByLine.named`
  turns it into the declaring file and the name, so the tools themselves read `name` as before.
  A tool that takes `line` routes it there before its own checks: `change_type` with
  `type_parameter` did not, and refused `line` (#407, 2026-11-13; `Tools.changeType`).
- `change_signature` (#342, 2026-11-12, the user asked for it and chose each): a new tool, over
  `add_throws`/`remove_throws` options on `change_type` (whose `return_type` stays); the
  implementations follow (overrides, lambdas, method-referenced methods, the returns of a body
  made void) and calls are left to the same batch, over declarations only and over an option that
  adds the exception to each caller up the chain; removing a `throws` a body still needs is
  refused, over removing it only from the declarations that do not. A new checked exception
  (2026-11-13, the user): `callers=propagate` by default, each caller declaring it up the chain,
  with `stop_at` (the user's addition) naming where it ends in a catch; `catch` and `refuse` as
  options. A catch rethrows unchecked, never swallows; a call inside an existing try gets one more
  clause on it, over a nested try.- `subclass_to_strategy` (#332, 2026-11-12, the user chose each): the model names the function's
  type (`type`) or the tool declares a nested interface named after the method, over
  java.util.function when it fits and over always an interface; the superclass's old constructors
  stay, delegating with its own body's function, over adding the parameter to every construction;
  the subclass is deleted and its uses retyped, over keeping it as a holder of the function.
- Enums (2026-11-13, `docs/sessions/enums-sketch.md`): the user chose the sketch's recommendations.
  Question 1 is option (a): the fixes and pins of increments 1 to 3, then slice 2 (a field on an
  enum, `add_parameter` with `values`), then slice 3 (`type_switch_to_method` on an enum), over
  all three slices now and over only the fixes. Slice 1 (constants to an enum, increments 9 to 11)
  and increment 12 (old-style switches) are not built: they wait for a friction entry or a task
  that needs them. Every other open question of the sketch: its Recommended option.
- Generators (2026-11-13, the user chose each sketch's recommended option; `docs/design.md`,
  "Generators"): one `build` argument on the edit tools over new tools, a refusal under
  `JREFACTOR_SKELETON_ONLY` as the way it is found, explicit types never `var`, a loop shell
  with `body` and no `op`, a call's arguments always in `args`.
- Tool definitions' size (#420, 2026-11-13, the user): `ServerTest.definitionsStaySmall` holds
  each tool's JSON, as `tools/list` sends it, to 1,700 characters, gorefactor's limit; the tools
  over it then are listed in `DEFINITION_OVER` at their size, a ratchet (lower the number when
  one shrinks), over one looser limit and over trimming them all first
  (`docs/sessions/descriptions-length-review.md`).
- `pull_up` onto an abstract declaration (#320, 2026-11-10): the supertype stays abstract, and
  the result says when nothing else in it is (the issue's "or offers to"), over making it
  concrete there: a class made concrete can then be instantiated, which the model did not ask for.
- Generics (2026-11-13, the owner, on `docs/sessions/generics-sketch.md`): the Recommended
  option of every open question there is the decision. Order: slice 2 (`extract_supertype` from
  a generic class), then the call-target check and `change_type`'s wildcards, then slice 1
  (`add_parameter` with `type_parameter`, over a new `add_type_parameter` tool and over
  `change_type`). Slice 2 is option (a): the new type gets exactly the type parameters its
  members use, plus those their bounds need, in the class's order, each declared as the class
  writes it; over all of the class's always, and over a `type_parameters` argument.
- Order 32 (one edit pipeline) is closed as partly done: `SignatureChange` and
  `MoveType` keep their own per-file import fix and gate.

## Where code goes

- A Maven project's source roots are `src/main/java` and `src/test/java` whenever either exists
  (`MavenBuild.sourceRoots`, #293): a root that does not exist yet is skipped when walking and
  parsing, so a test created before `src/test/java` is type-checked and gets its stubs. It had
  been outside every root, so `create_file` wrote it unchecked.
- `Jdt` owns every parser; `grep ASTParser` finds `Jdt` only. A unit parsed from a
  file carries its path (`Jdt.fileOf(unit)`), which a check across parses can key by.
  An exception ECJ throws while resolving becomes a refusal there (`Jdt.compilerFailed`, #405);
  one the caller's `each` throws passes through unchanged. A type that `parseAll`'s files do not
  hold is resolved only as far as the batch needed it, and its scope is gone after: the binding of
  a single static import of its method threw (#411), so `parseAll` adds the files those imports
  name (`Jdt.staticImported`); `getDeclaredMethods()` of such a type logs "Could not retrieve
  declared methods" and returns none.
- What the gate counts is `TypeCheck.counts` (every JDT error but those a named module would not
  have, #438); both the full and the narrowed count go through it, and a change to what it drops
  bumps `TypeCheck.FORMAT`, so saved counts are made again. A count depends on whether a source
  root holds `module-info.java`: the narrowed check starts over when that changes.
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
  under a source root. It parses the text first (`Edits.resultParses`, #178): a refactoring's
  output that does not parse is refused as the tool's own fault, an edit's as syntax to fix.
- An array creation (`new int[n]`, or `{1, 2}` alone) is `Effects.Kind.NEW_ARRAY`, not a change
  (`changes()`); `Behavior.flow` refuses it in several landings only (`newArraysNow`, #178).
- `Session` holds all per-project state (undo history, deferred flag, library
  index, last type check, `editing`, `preserveExceptions`); there is no static
  mutable state. `Server.Quiet` holds the per-server state.
- `Opened.topLevel` takes a class or interface only; a record, enum or annotation type of that
  name is refused saying which it is and listing the file's classes and interfaces
  (`Opened.notAClass`, #155). The other callers of `MemberMove.typeNamed` with their own
  wording (`PullUp`, `MoveMember`, `AddResult`, `InlineClass`, `SubclassToStrategy`) were not
  changed. `Opened.classOrRecord` also takes a record (#439, 2026-11-13: `inline` only, so far);
  `MemberMove.member` takes any `AbstractTypeDeclaration`. A tool that should take records
  switches to it once a golden case shows its algorithm holds for one.
- A new tool starts from `Opened` (read, parse, find the target) and lists its
  up-front checks as `Precondition`s; mid-algorithm refusals stay `throw`
  (`docs/sessions/refactoring-shape.md`). `Rename`, `MoveType`, `Extract`,
  `Declarations` and the signature tools keep their own resolvers (roadmap #74).
- What code reads, writes and calls comes from `Effects.of(node)`
  (`docs/sessions/one-effects-analysis.md`); a new effect kind goes in
  `Effects.Kind`. `Effects.mayThrow()` is the syntax-only "may throw";
  `Effects.later(node, root)` and `Effects.sometimes(node, root)` say where code
  runs later, or only sometimes or more than once, than the code that holds it
  (#76; the branches of statements too). A record's own accessor (`isSyntheticRecordMethod`) is no
  call: it reads a final field (#329). `Behavior` asks `changesNow()` of moved code: a lambda
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
  leaving a guard or a catching try is refused there; entering one is reported. Which guard
  keeps a division or a dereference is `Guards` (#173): `operands` (null for anything else that
  may throw, which any guard keeps), `left` (the conditions a move leaves), `tests`.
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

- `move` of a member type writes a read of a record's field that crosses the new boundary (a
  nested record's private fields are visible to the whole top-level type) as an accessor call
  (#184, issue #440, `MoveType.accessorCall`), refused when the accessor does more than return
  the field (`returnsField`). JDT's `findDeclaringNode` gives null for a record's field:
  `NameCheck.declaration` takes the component of that name.

- `move_file` (#81, 2026-11-04) is `MoveFile`: each file through `Gate.write` (a null
  text deletes), so a move, copy or delete is one undo. Text only; Java under a
  source root is refused for `move_type`.

- `change_type` with `parameter` or `return_type` (#82, 2026-11-04) is
  `Signatures.changeType`: the family's declarations and typed lambdas, by
  `SignatureChange.replace`; calls are left to the gate.

- `replace_declaration` with empty content is a safe delete (#83, 2026-11-04):
  `SafeDelete.check` names the uses by binding before the gate, and refuses an
  override of a concrete method unless it only calls super or has the same body
  (`SafeDelete.SameMeaning`, #322). The stub goldens that deleted a used member
  (`stub_after_delete_*`) are now refused there, before the stubs run.

- `extract_supertype` (#85, 2026-11-04) is `ExtractSupertype`, composed in one call
  from the gate (the new file), `AddSupertype`, and `PullUp` per member. A member
  `pull_up` or `push_down` removes now takes one blank line beside it with it
  (`MemberMove.removal`); three goldens that pinned the leftover blank line changed.
  From a generic class (#144, 2026-11-13), `ExtractSupertype.generics` picks the type
  parameters: names in the extracted nodes that bind to the class's type variables, closed over
  bounds, each `TypeParameter` copied as text. The class's clause is the names, passed to
  `AddSupertype.run` as the supertype's text. For `kind=class` (#145) the nodes are the whole
  members and the old superclass's type; `PullUp` needed no change. Since #179 (2026-11-13) the
  new class is abstract when the old superclass leaves an abstract method no member implements
  (`Skeleton.leftAbstract`, which `unimplemented` now calls) or a member is abstract, and passes
  through each old-superclass constructor the class's `super(...)` calls use
  (`ExtractSupertype.constructors`); the defaults chosen are in the roadmap row.

- A type written into code names a member type of the type around that code, or of one around
  that, by its simple name: `Extract.typeName(b, at)` (#328, 2026-11-10; `extract_function`
  passes the member's type). `Extract.typeName(b)` qualifies every member type.

- `extract_variable` of a lambda or method reference declares the local with its target type's
  wildcards replaced by their bounds (`Extract.groundTypeName`, JLS 9.9; #298): `Function<? super T,
  ? extends R>` is `Function<T, R>`, the type the lambda had.

- `loop_to_stream` (roadmap #132, 2026-11-10) is `LoopToStream`: an enhanced for over an
  `Iterable` read into stages from the outside in (an if with no else is `filter`, a first local
  `map`, the rest `forEach`, or `toList()`/`collect` into a list declared just before; an if/else
  whose paths each add is a `flatMap` of a conditional, `LoopToStream.branch`; a local declared just
  before and set to one constant is a flag, a `reduce` of per-element booleans, and one added to or
  incremented is a sum or count, `LoopToStream.total`). `run` reads the body (`stages`, a `Read`)
  and picks one `Rewrite`: `computed` (a flag, `total`, or `search` when the flag is set then
  `break`s, the only break `checkBody` lets through), `collect` or `forEach` (`entries` for a
  map's `entrySet()` used only through `getKey()`/`getValue()`, as `map.forEach`), or `grouped`
  (a `HashMap` of `ArrayList`s filled by `computeIfAbsent(...).add(...)`, as `groupingBy`; the null
  key it now throws on goes through `Exceptional.change`). Its rules
  are in `docs/sessions/convert-loop-sketch.md`, its target cases in `docs/sessions/loop-examples.md`
  (three from the user's darktable project); `convert_loop` is the statement forms.

- `change_signature` (roadmap #137, 2026-11-12) is `ChangeSignature`, on `MethodFamily.of` with
  `references` (a method reference is kept in `family.references`, not refused; `family.sources`
  holds each parsed file, so a referenced method's file is edited once) and
  `SignatureChange.apply` with its own fix text. `voidReturns` rewrites returns by `pure` and
  `statement`; the gate judges calls, bodies that still throw and unreachable catches.
  `ThrowsCallers` is `callers`: every file parsed into `family.sources`, calls and declarations
  indexed by `Signatures.key`, a queue of the keys that now throw (`spread`), each call either
  handled already (`catches`, `allows`), declared further (`declare`, by `Signatures.family`) or
  caught (`catchAt`); `write` emits the clauses and the tries as inserts, so they do not overlap
  other edits. The exception's supertypes come from a probe field parsed in the target's file.
- `subclass_to_strategy` (roadmap #136, 2026-11-12) is `SubclassToStrategy`: one `Holding` parse
  of the files that name the subclass and the superclass's file, text edits per file, each
  through `Imports.fix` (the subclass's imports as the chosen ones) and all through
  `Gate.applyOrRefuse`, the subclass's file as null. `usesInstance` refuses a body that needs the
  instance; `construction` maps `new Sub(args)` through the subclass constructor's `super(...)`;
  `samOf` finds a named type's method by parsing the superclass with a probe field.

- `convert_loop` (roadmap #138, 2026-11-11) is `ConvertLoop`: an index for or a for over an
  iterator to an enhanced for, and an enhanced for to either; each pair writes its text edits
  directly (`indexToEach`, `iteratorToEach`, `toIterator`, `toIndex`), sharing `stable` (the
  walked list or array cannot change between passes) and `unchanged` (no call changes the list).
  It borrows `LoopToStream.elementOf`, `unused`, `primary`, `reindent` and `CHANGES`. A for and a
  while (`forToWhile`, `whileToFor`) go through `continues` (the continues of this loop, by label
  or innermost loop), `before` (statements put before one) and `last` (statements put last in a
  body). A do-while to a while is `while (true) { body; if (!cond) break; }` (`doToWhile`, the
  user's choice of 2026-11-11 over copying the body; a `continue` is refused), and a while to a
  do-while is guarded by `if (cond)`, refused unless the condition only reads locals.

- A construction with a class body (`new T(...) {...}`, an enum constant with a body) resolves to
  the anonymous class's own constructor, not `T`'s: ask `Signatures.constructed(binding)` for the
  one the arguments go to (#142, 2026-11-13). `MethodFamily.findCalls` does; `ByLine`,
  `SafeDelete`, `ThrowsCallers` and `Signatures.addComponent` still compare the raw binding.

- `add_parameter` with `name` an enum (#150, 2026-11-13) is `EnumField`, entered first from
  `Signatures.addParameter` (it returns null when `name` is no enum of the file): one file's text
  edits (constants' arguments by `Signatures.insertAt`, the constructor's parameter and
  assignment), then the field, constructor and accessor through `Declarations.plan`, all through
  `Edits.gated`. `values` is an object argument (`Arg.json`, read by `Args.strings`).
  `EnumField.field` is the edit without `Args` (a values map in, the text edits and the members
  out), which slice 3 writes through.

- `type_switch_to_method` over an enum (#151, 2026-11-13) is `EnumSwitch`, entered from
  `TypeSwitch.run` before the type-pattern cases are read: the field form takes the case values'
  text into `EnumField.field`, checks them with `NameCheck`, and applies the members by
  `Declarations.plan` onto the edited enum text (one whole-file edit for the enum's file, the
  switch's edit inside it when the switch is in the enum's file). `TypeSwitch.usesOnly` takes the
  name its refusal gives the case's variable ("the switch's value" for an enum). A bare `throw`
  after the arrow is a body as a block is (`EnumSwitch.arms`, #435); `TypeSwitch.switchCases`
  still refuses one. A bare static in a case is refused unless `EnumSwitch.sees` it from the enum
  (its own, or an enclosing type's that nothing on the way out hides; #436).
  The method form (#152) is `EnumSwitch.method`: the constants' bodies are text edits on the
  enum's text (`EnumSwitch.inBody`), checked by `NameCheck`; the enum's own method is then added
  by `MemberMove.appendTo` on that edited text, reparsed, since `appendTo`'s `;` after the last
  constant would overlap a body written after it. `TypeSwitch.body` takes the indentation, and
  `TypeSwitch.Case` is shared.
  A member type is found by `TypeSwitch.fileAround` (the file of its outermost type; `fileOf`
  stays top-level only, as `InlineClass`, `SubclassToStrategy` and `ByLine` rely on its null for a
  member type) and `TypeSwitch.declaration` (by `Names.typeLabel`, without bindings) (#154).

- `extract_variable` with `as` (#86, 2026-11-04) is `Introduce`: a constant, a field,
  or a parameter through `Signatures.addParameter`. As a parameter (#125, #161) the expression
  is a `ParameterValue.InMethod`, the record `remove_parameter` `value` uses, so the per-call
  rules (`ParameterValue.perCall`) are one set for both directions; the occurrences are replaced
  in the same `SignatureChange` (`addParameter`'s `more`, which may also refuse by the family),
  one write and one undo. With a call it is checked as a `Behavior.Move` to the body's first
  statement (`Behavior.flow`). A call on the method's own object or a static method named alone
  (#169) is written per call by `InMethod.written` (`at` for the extraction; `inPlace`, without
  receivers, for `remove_parameter`'s messages) and checked by `ParameterValue.sameTargets`,
  which parses the calling file with the argument in place and compares each call's binding
  (by key, or an override) and the probe's not-visible problems.

- `ParameterValue` (roadmap #147, 2026-11-13) holds what an expression of a method's parameters
  needs, for both directions: `byParameters` and `perCall` (`extract_variable` `as=parameter`:
  each call passes the expression), and `inMethod`, `passes`, `quiet` (`remove_parameter`
  `value`: the method computes it). `inMethod` parses the declaring file with the value declared
  where the body starts, under a name no source has, so its names carry the bindings they have
  there; `passes` is an `ASTMatcher` with the call's arguments in place of the parameters, other
  names by binding key. `Fold.Holes` was not reused: it finds what a parameter stands for, where
  here the call's arguments give it, it does not look through parentheses, and its
  `String.format` rule would be wrong here.

- `convert_lambda` (#87, 2026-11-04) is `ConvertLambda`: lambda, method reference and
  anonymous class to one another; the type check catches an ambiguous overload.

- `convert_to_record` (#88, 2026-11-04) is `ConvertToRecord`; a record's generated
  `equals`/`hashCode`/`toString` change behavior, so the class must define them or the
  model must pass `value_semantics=true` (this session's decision).

- `extract_type` (named `extract_struct` until 2026-11-06, the user: "struct" is Go's word; the old
  name is an alias in `Tools.ALIASES`) takes one shape since 2026-11-06 (`name`, `members`, `new_type`, `as`; `new_type` was `struct` until 2026-11-07, still accepted): the case
  follows from what `name` is; old argument names are renamed in `Tools.call` (`ARG_ALIASES`).
- `extract_type` with `fields` builds the holder in each constructor for final fields set there
  (#345, 2026-11-11, `ExtractType.buildInConstructors`); the new class gets the constructor.
- `extract_type` (#89, 2026-11-04) is `ExtractType`: a parameter object, through
  `MethodFamily` and `SignatureChange.replaceRange`; with `fields`, extract class
  (`ExtractType.fields`), for fields only their class uses. On a record, `fields` is
  `ExtractType.components` (#118, 2026-11-05): constructions, accessor calls and field reads
  found by the record's binding key in one `parseAll`; `value_semantics=true` accepts the
  changed `toString` (the user's choice; the options it beat are in the contract).

- `move` of a method takes the private methods only it calls along (#317, 2026-11-10, the user:
  "if it doesn't break anything"): `MoveMember.along` orders them callees first, and
  `withAlong` moves each by its own `run` in the same call, so one undo reverts all. Over a list
  of members to move together (the issue's other idea). Not fields: those stay refused.
- `move` keeps the `receiver` parameter even when the moved body does not use it (the user,
  2026-11-10, #330): over dropping it, which would also stop a null instance from failing at the
  call. `remove_parameter` drops it after, when the model wants that.
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

- `build` (#149, 2026-11-13) is `Build`: `Primitives.built` hands it the argument as sent
  (`Args.raw`), the range it replaces and the indentation; `Build.statements` returns the text
  and what it inferred (`Built.said()`, after the tool's usual line). An entry is an `Entry`
  (`statement()` writes it; a new kind adds a key in `generator` and a case there). The drafts
  are parsed after `Imports.fix`, so a type the build names resolves before it is imported;
  entries are found again by line (`Draft.at`), one statement starting per line. A golden case
  passes the list as `build.json`. A loop's body is decided one draft after its head
  (`Build.scope` runs until nothing new is decided), since what an inner loop walks can name the
  outer loop's variable; an entry decided in an earlier draft is not decided again (`decided`).
  A guard (#164) needs no decision: it is written as typed (`Entry.guarded`) and checked in
  the last draft (`checkGuard`), as a call is; `surround`'s loop object (#163) is one `loop`
  entry whose body is the replaced lines (`Build.around`).
  On `replace_expression` (#165), `Build.expression` drafts one entry in place of the
  expression and finds it again by offset (the import fix changes only lines above it).

- `NameCheck` (#77, 2026-11-04) is the general check that moved code keeps its
  meaning, by binding key per identifier, before the edit is written. A new tool that
  moves code should call it (`meanings` of the source fragments, `require` with the
  edits, and the call sites' arguments when the tool rewrites calls); `inline_function`,
  `inline_variable`, `convert_lambda`, `pull_up`, `push_down`, `move_member`,
  `extract_method` and `move_type` do. It skips qualified names, recovered bindings
  and type variables, and compares a method's name only with methods' (`NameCheck.spaced`, #344:
  `skipped(skipped)` was taken for a capture).

- `CallTargets` (roadmap #146, 2026-11-13) is the check that a change keeps what every call runs:
  `CallTargets.of(units)` before the write (units of one parse of the project's files), `require`
  after it, which parses the files again and refuses when a call, `new`, `this(...)`, `super(...)`
  or method reference runs another declaration. A declaration is its file and its number among
  the file's methods, since a signature change alters its binding key; so the change between the
  two may add or remove no call and no method in those files (it throws `IllegalStateException`
  when the calls differ in number or kind). It is not a rule of `Behavior.check`: that judges code
  a tool moves, before the write, and this compares two parses around a write that moves nothing.
  Inside a batch with check `"end"` it runs as `Rename.checkMeanings` does: the step's files are
  written, the parse reads them, a call that does not resolve is skipped (the end check judges
  it), and a refusal aborts the step, which puts its files back. `change_type` calls it; a tool
  that changes a signature or makes a type generic should. A virtual call (a `MethodInvocation`
  of an instance method) whose new target is a method its old target overrides passes
  (`Target.overrides`, by `CallTargets.overridden`, #157): `value.equals(x)` names
  `Object.equals` once `value` is a `T`, and still runs `String.equals`.
  It also keeps overrides (#159): `of` records each method of the checked files that overrides
  another, by "its place > the overridden's place" (`overrides`), and `require` refuses one lost
  or gained, with the caller's fix (`require(project, units, what, fix)`).

- `add_parameter` with `type_parameter` (roadmap #156, 2026-11-13) is `TypeParameters`, which
  `Tools.ADD_PARAMETER` routes to before `ByLine.named`: one `Holding` parse of the files that hold
  the class's name, each `SimpleType`, `NameQualifiedType` or `QualifiedType` that binds to the
  class (compared by its erasure's qualified name: the declaration's key changes once it is
  generic) rewritten by where it stands (`exempt`, `fixes`, `inInstanceCode`), each file through
  `Imports.fix` and all through `Gate.applyOrRefuse`; then one parse for `fullDiamonds`,
  `requireNoRawUse` and `CallTargets`. A `var` is a `SimpleType` that binds to the variable's
  type: `named` skips it. `parameter` and `type` are not `.required()` in the schema any more;
  `Signatures.addParameter` refuses a call without them. The decisions (the user, 2026-11-13,
  each the sketch's recommendation) are in the contract's section.
  `members` (#157) is `TypeParameters.typed`: each named member found as `Declarations.find`
  finds one, and in its declared types (not its body) each type node that binds to `value`'s
  qualified name, outermost first, becomes the parameter; the body is the gate's.
  A method (#158) is `TypeParameters.toMethod`, routed by a syntax-only `Declarations.find` before
  the `Holding` parse: `MethodFamily.of` with references kept; each call's and reference's types
  (`types`: return and parameters by qualified name) are read before the write and compared after
  by its kind and number in its file (`ordinal`), since the declarations' edits move offsets.
  `change_type` with `type_parameter` (#159) is `TypeParameters.bound`, which `Tools.changeType`
  routes to first: the bound is the text after the `TypeParameter`'s name, replaced in each
  declaration (one for a class, the family's for a method); casts to the parameter are found by
  the type variable's key before the write and reported when its erasure differs after.

## Testing

- A golden case with `skeleton_only=true` runs with `Session.skeletonOnly` on (roadmap #116).
  Under the flag, `Generated` also refuses typed bodies a command writes (2026-11-06): a
  field-assigning constructor, an override that only calls `super` or throws, a statement wrap;
  also in a type sent whole through `replace_declaration`, and in each method that
  `replace_statements`, `replace_expression`, `insert_statements` or `replace_text` adds or changes
  into that shape (`Generated.checkChanged`, 2026-11-07); a member already in it is left alone.
  And (#167, 2026-11-13) statement content that is one statement `build` writes from names
  (`Generated.checkBuild`, the generators' discoverability mechanism the user chose).
- A tool's definition that grows past its limit fails `ServerTest.definitionsStaySmall`: shorten
  the description or its arguments' text, rather than raise the limit (ask the user first).
- A golden case with `classpath=junit` in `args.properties` gets the JUnit jars of the test
  JVM (`GoldenTest.junitJars`), for a tool that needs a library to resolve.
- Every golden write case is undone by `undo` and must return to `in/`, and the
  cached type count must equal `TypeCheck.uncached`.
- A rule in the gate that skips work (such as the narrowed check) is checked by
  a stress run on a real project before it is merged: the narrowed check's first
  rule missed three of 60 random edits, all through inheritance, though every
  example test passed. `NarrowCheckStressTest` and `RefactoringStressTest` are
  opt-in (`-Djrefactor.stress.root`, `.seed`, `.counts`); a killed run is put
  back by the next one. `RefactoringStressTest` also takes `.tools` (entries of its `TOOLS`,
  comma-separated: the 2026-11-13 options are the ten ending `_type_class`, `_type_method`,
  `_enum_values`, `_value`, `_bound`, `_wildcard`, `_enum`, `_parameter_all`, `_parameter_call`,
  `_generic`) and `.refusals=true` (each refusal whole: read them, as wrong refusals count as
  bugs too). Commons Lang here (2026-11-13, `docs/sessions/stress-new-options.md`):
  `GIT_LFS_SKIP_SMUDGE=1 git clone --depth 1 https://github.com/apache/commons-lang <dir>` outside
  the worktree; it has no `./mvnw`, so the classpath comes from `mvn` on the PATH (the proxy
  `settings.xml` first); then `flock /tmp/jr-heavy.lock ./mvnw -q -o test
  -Dtest=RefactoringStressTest -Djrefactor.stress.root=<dir> -Djrefactor.stress.calls=300
  -Djrefactor.stress.seed=12 -Djrefactor.stress.counts=true`: about 3.5 s a call on 2 cores, 7 s
  an edit for `NarrowCheckStressTest`. Lang is Java 8 source, so it has no target for
  `type_switch_to_method` over an enum (switch expressions and arrow cases only). For that, and
  for Java 17 shapes (records, sealed types, a named module), smallrye/jdk-classfile-backport
  (2026-11-13, `docs/sessions/stress-enum-switch.md`): cloned the same way to a directory outside
  the worktree, no `./mvnw` either, 258 files, about 1.5 s a call; `-Djrefactor.stress.tools=
  type_switch_to_method_enum,add_parameter_enum_values` for the enum entries. `.refusals=true` also
  prints each applied call's result. About 40% of the enum entry's picks there are switches over
  `int` constants (the picker reads no bindings), refused by design.
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
- The generator task set is `eval-tasks/depot.json` (2026-11-13, `docs/sessions/generator-tasks.md`,
  issue #376): three tasks of statements inside existing methods (a loop over a map field, a call
  chain, a parameter every call derives), each doable in one tool call.
- The A/B arms for the generators (issue #376): `jrefactor-ab-server` (with `build`),
  `-skeleton` (with the refusal), `-no-build` (`JREFACTOR_NO_BUILD=1`: the statement edits as before
  `build`, the create aliases refused), `-aliases` (`JREFACTOR_PUBLISH_ALIASES=1`: `create_loop`,
  `create_call`, `create_guard` and `replace_parameter` published after the tools). The server publishes `Tools.published(session)` and a batch
  looks its steps up by `Tools.named(name, session)`; the CLI still uses `Tools.ALL`.
- The four generator arms ran on 2026-11-13 (`docs/sessions/ab-generators.md`, 24 runs, $2.79):
  all passed, within noise of each other; `build` was used only when the skeleton refusal asked
  for it, the create aliases never, `replace_parameter` by name in place of `remove_parameter` `value`.
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
  newline joins the lines; so does an unquoted heredoc (`<<J`, to expand a variable), which
  joined a tool description's text-block lines on 2026-11-11. Quote the delimiter. The Edit tool avoids it.
- Check SDK and library APIs with `javap` against the jars, not from memory.
- JDT's `TypePattern.getPatternVariable()` returns a node without bindings; the parse resolves
  `getPatternVariable2()` (2026-11-06, `TypeSwitch`).
- JDT's `setEnvironment` needs one encoding per source path entry.
- JDT's headless parser checks every file as the unnamed module: `setEnvironment` has no module
  path, and the resolver gives each unit a null module name (#438, 2026-11-13, by `javap`; no
  public API reaches it). A named module's errors that differ are filtered in `TypeCheck.counts`
  (only the sealed permits one so far); `module-info.java`'s own errors are pre-existing noise
  (every exported package reads as missing), and `create_file` of one is refused as not parsing.
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
- A project type `Jdt.parseAll` reads from the source path (not one of its files) is resolved
  only as far as the batch needs it, and JDT frees its scope when the batch ends: asked later,
  `getDeclaredMethods()` logs "Could not retrieve declared methods" and answers none (#431).
  `Jdt.completeMembers` asks, during the batch, each type a unit's names bind to and its
  supertypes, member types and declaring class; a type reached only through another binding (a
  method's parameter or return type) is not, so read its members inside `parseAll`'s callback or
  parse its file with the batch. `Jdt.parse` of one file keeps them.
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
  default `java` is 25. Since 2026-11-11 the project builds on 25 too, and CI runs `verify` on
  21 and 25 (the user's choice, `docs/sessions/build-on-jdk25.md`): JDK 25's `javac` warns on
  a doc comment attached to no declaration, which `-Werror` fails on, so a stray one broke the
  MCP server's rebuild there. The release jar stays built for 21 and runs on 21 or later.
- CI skips the build for docs-only changes. Dependabot opens weekly PRs for
  Maven and Actions.
