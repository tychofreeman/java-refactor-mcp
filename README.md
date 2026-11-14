# java-refactor-mcp

An MCP server that gives an AI coding assistant behavior-preserving
refactorings for Java, built on Eclipse JDT Core. The model picks the step
(rename this, extract that, move this method); the server checks that the
step keeps behavior, applies it to every use, type-checks the project before
and after, and rolls back anything that doesn't compile. It is the Java
counterpart to [go-refactor-mcp](https://github.com/tychofreeman/go-refactor-mcp)
(gorefactor), with the same tool names where the operation is the same.

## What you get today, and what is not proven yet

What you get:
- **Changes that keep compiling.** Every step is type-checked before and
  after it applies; a step that breaks the build is rolled back, and a
  `batch` of steps rolls back as a whole.
- **Changes that reach every use.** A rename, a signature change or a move
  is carried to every reference and override, found by JDT's bindings
  rather than by search. Imports are added and removed for you.
- **Refusals instead of silent behavior changes.** A refactoring that could
  change what code does (evaluation order, side effects, name capture) is
  refused, with what to send instead. A change to exceptional behavior
  outside a guard or a try/catch is named in the result, or refused with
  `preserve_exceptions`.
- **Test first that compiles.** A test that calls code that does not exist
  yet gets "not implemented" stubs, typed from the use, so the project
  still builds; a constructor whose arguments each fit one field sets them.
- **Boilerplate written for you.** Final fields added to a class with no
  constructor get one that sets them, and new members are placed by kind
  (fields with fields, constructors after them, the rest at the end).
- **Undo** for every call and every batch.

What is not proven: that it saves tokens. On four small refactoring tasks
(Sonnet, four runs per arm), every run passed both with the tools and by
hand, and the tools cost about the same: $0.095 a run against $0.093 in the
latest run, down from 32% more in the first. On three tasks that write
statements inside existing methods (2026-11-13, two runs each, tools only),
every run passed, and `build` was the cheapest way, $0.109 a run against
$0.129 without it; but a model chose it only when a refusal asked for it. See
`docs/design.md` (roadmap row 13) and `docs/sessions/`.

## The tools

- **Read:** `inspect`, a file without method bodies, then the ranges you
  will change.
- **Refactorings:** `rename` (any kind of name); `extract_function`,
  `extract_variable` (also as a constant, field or parameter),
  `inline` (a local variable, or a method's calls); `add_parameter`, `remove_parameter`,
  `move_parameter`, `add_result`, `change_type`, `extract_type` (a parameter object, or
  extract class); `move` (a nested type to its own file, a type to a package, a method or
  static field to another class, a file),
  `pull_up`, `push_down`, `add_supertype`, `extract_supertype`;
  `function_to_method`, `encapsulate_field`, `convert_lambda` (lambda,
  method reference, anonymous class), `convert_to_record`; `fold` (replace
  code that does what a method does with a call of it); `move_statement`,
  `invert_if`, `remove_else`, `type_switch_to_method`; `loop_to_stream`
  (an enhanced for as a stream), `convert_loop` (an index or iterator for
  and an enhanced for, a for and a while, a while and a do-while, either way),
  `subclass_to_strategy` (a subclass that overrides one method as a function
  its superclass holds), `change_signature` (a method's return type and
  `throws`, in every override, lambda and method-referenced method).
- **Edits**, which change behavior on purpose and are still type-checked
  and undoable: `create_file`, `add_declaration`,
  `replace_declaration` (empty content is a safe delete),
  `replace_statements`, `insert_statements`, `replace_expression`,
  `add_case`, `add_element`, `replace_text`. `insert_statements`,
  `replace_statements` and `replace_expression` also take `build`: calls,
  constructions, loops and guards written from names, their types read from
  the code.
- **Plumbing:** `batch` and `undo`.

The arguments and results of each are in
[docs/tool-contract.md](docs/tool-contract.md).

## Requirements

- **Java 21 or later** to run the server (each release is built for 21 and
  tested on 21 and 25). Building it, or using the launcher, needs a full JDK
  21 or later (with `javac`), as `JAVA_HOME` or on `PATH`.
- **A Maven project:** a `pom.xml` at the root, sources in `src/main/java`
  and `src/test/java`. The server gets the classpath by running the
  project's `./mvnw`, or `mvn` on the `PATH`. Gradle, multi-module builds
  and other source layouts are not supported yet; without a working Maven
  build the server still runs, but names from libraries do not resolve and
  refusals say so.

## Install

Every change to `main` that passes CI is released with the jar attached.
Download the latest:

```sh
curl -LO https://github.com/tychofreeman/java-refactor-mcp/releases/latest/download/java-refactor-mcp.jar
```

Or build it from a clone (`./mvnw package` makes
`target/java-refactor-mcp.jar`; the first build needs Maven Central). The
server reports the release it was built as, or `devel`. To upgrade,
download the jar again and reconnect the server in Claude Code (`/mcp`).

## Using it from Claude Code

**1. Register the server.** It serves one project: the directory given as
its argument, or, without one, the directory it starts in. For one project:

```sh
claude mcp add jrefactor -- java -jar /path/to/java-refactor-mcp.jar /path/to/project
```

Or share it with a project by committing a `.mcp.json` at its root, with no
project argument, so it serves the project Claude Code opens (see
`examples/mcp.json`):

```json
{
  "mcpServers": {
    "jrefactor": { "command": "java", "args": ["-jar", "/path/to/java-refactor-mcp.jar"] }
  }
}
```

From a clone, `bin/jrefactor-mcp /path/to/project` is a launcher that
builds the jar first when it is missing or stale.

**2. Tell Claude to use it.** Without instructions that ask for them, a
fresh model doesn't call the tools: it edits files as it always has
(gorefactor's A/B runs found this, and the Java runs used the same
instructions). Copy `examples/CLAUDE.md` into your project's CLAUDE.md, or
merge it into the one you have, and put your project's name in its heading.
It asks for the moves in "Using the tools well" below.

**3. Try it:** in a Maven project, ask something like *"use the jrefactor
tools to extract the validation in OrderService into its own method"*. Run
`/mcp` in Claude Code to see the server's status.

## Using the tools well

`examples/CLAUDE.md` asks for four things:

- **Make the change easy, then make the easy change, test first.** Refactor
  first, while the tests pass (rename to what a thing will mean, extract
  what you will change, add the parameter the change needs), until the
  change is small and local; then write the failing test and make it pass
  with the edit tools. Models extend the code that is there unless told to
  reshape it first. A test written with `create_file` (`test_for`) or
  `add_declaration` gets stubs for what it calls, so it fails on its
  assertion, not on compiling.
- **Read through `inspect`, not Read or `cat`.** Every result stays in the
  conversation and is sent again on each later turn: inspect a file first,
  then only the methods or line ranges you will change.
- **Work as a derivation.** Write the change as operations that keep
  behavior, ending in the one small edit that changes it, and send them as
  one `batch`. Near-duplicates: write the shared method (or extract it from
  one copy), then `fold` the copies onto it. Dependent steps (a signature, then its
  callers) go in one `batch` with check `"end"`; a refusal changes nothing and says what to
  send instead. One parameter: `add_parameter`,
  `remove_parameter`, `move_parameter` or `change_type`, every call and
  override included.
- **Look for a sequence before editing by hand.** When no tool seems to
  make a change, look for two or three that compose to it; make it by
  hand only then, and log it in `docs/friction-log.md`.

**Guards (optional hooks).** Sessions read and write Java by shell out of
habit even when asked to use the tools. Two PreToolUse hooks turn the habit
into a refusal that names the tool to use: `bin/read-guard` refuses reading
a `.java` file other than through `inspect` (Read, or `sed`, `cat`, `head`,
`tail`, `awk` or `grep` naming one), and `bin/write-guard`, an experiment,
refuses changing one other than through the tools (Edit, Write, MultiEdit,
or `sed -i`, `tee`, `cp`, `mv`, `rm` or a redirection into one). Files under
`resources/` are allowed, and so are `./mvnw spotless:apply` and git. They
run from a clone with the jar built (`./mvnw package`; without the jar they
allow everything): add `examples/guards.json` to your project's
`.claude/settings.json`, with the path to your clone.

**Skeleton-only creation (an experiment).** With `JREFACTOR_SKELETON_ONLY=1` in
the server's environment (the `env` of its entry in `.mcp.json`), a new Java
file can only start as a type's header: `create_file` with `declaration`
(`final class Cart implements Priced`, stubs for what the supertypes require)
or `test_for` (`Cart.total`, a red test), and members come one at a time
through `add_declaration`; a typed constructor that only assigns fields, an override that
only calls `super`, and a typed `try` or `if` around unchanged statements are refused for the
operation that writes them (`constructor`, `implement`, `surround`). It tests whether building code from operations,
as in an IDE, beats typing whole files (`docs/design.md`, "Constructive
transformations").

## Troubleshooting

- **The server is connected but Claude never calls it.** That is the
  default; add `examples/CLAUDE.md` to your project's CLAUDE.md (step 2).
- **"cannot be resolved" for library types.** The classpath comes from
  Maven: check that `./mvnw` or `mvn` runs in the project, and that it is a
  single-module Maven project. The refusal names the cause.
- **The tools don't show up.** Claude Code reads `.mcp.json` when a session
  starts, so one added during a session takes effect in the next. `/mcp`
  shows whether the server started; a wrong Java version shows up as a
  failed start.
- **"release version 21 not supported" when building.** The `java` found is
  a runtime without a compiler, or older than 21: set `JAVA_HOME` to a JDK
  21 or later.

## Using the CLI

From a clone, `bin/jrefactor` runs the same tools from a shell, one call per
run, with the same arguments and results:

```sh
bin/jrefactor list
bin/jrefactor rename file=src/main/java/p/Bed.java line=7 name=t new_name=total
bin/jrefactor rename '{"file":"src/main/java/p/Bed.java","line":7,"name":"t","new_name":"total"}'
bin/jrefactor -C /path/to/project batch @steps.json
bin/jrefactor add_declaration file=src/main/java/p/Bed.java type=Bed content_file=- <<'JAVA'
  int size() {
    return plants.size();
  }
JAVA
bin/jrefactor undo
```

A text argument comes from a file with `key_file=path` (`content_file`,
`new_file`, `old_file`), or from stdin with `key_file=-`, one per call.

Code for a JSON object (a batch, say) goes after it in sections, unescaped: a
line `@@name` starts one, running to the next such line or the end, its last
newline dropped, and the JSON string `"@name"` stands for it.

```sh
bin/jrefactor batch - <<'BATCH'
{"steps": [
  {"tool": "add_declaration", "args": {"file": "src/main/java/p/Bed.java", "content": "@size"}},
  {"tool": "rename", "args": {"file": "src/main/java/p/Bed.java", "line": 7, "name": "t", "new_name": "total"}}
]}
@@size
int size() {
  return plants.size();
}
BATCH
```

A section no string names, and a name with no section beside others (a heredoc
that ended early), are refused.


Exit status: `0` done, `2` refused, `1` error.

## Help improve the tools

Which tool to build next is chosen from evidence: each time a change had to
be made by hand because no tool, or no sequence of two or three tools, made
it. If you would like to help, add a friction log to your project's
CLAUDE.md, as gorefactor's README describes ("Help improve the tools"), and
open an issue with the entries. This repository's own log is
`docs/friction-log.md`.

## Contributing

Build and test with `./mvnw verify`; one golden case, or a few, with
`./mvnw test -Dtest=GoldenTest -Djrefactor.golden=add_case,rename_*` (`ProbeTest`
and `-Djrefactor.probe=...` for the behavior probes). How the server is built, the rules for
working in this repo, and the roadmap:

- [CLAUDE.md](CLAUDE.md): how Claude Code works in this repo
- [docs/design.md](docs/design.md): architecture, decisions and the roadmap
- [docs/tool-contract.md](docs/tool-contract.md): tool names, arguments and results
- [docs/session-notes.md](docs/session-notes.md): what past sessions learned
- [docs/how-this-project-was-built.md](docs/how-this-project-was-built.md): the arc of the project
- [docs/friction-log.md](docs/friction-log.md): where the tools got in the way

## License

MIT.
