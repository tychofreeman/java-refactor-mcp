# CLAUDE.md

Instructions for Claude Code in this repository.

## What this repo is

`java-refactor-mcp`: an MCP server that gives Claude type-checked Java
refactorings and edits, built on Eclipse JDT Core. Companion to gorefactor
(`tychofreeman/go-refactor-mcp`). Keep Java-specific decisions here; keep the
shared tool contract in `docs/tool-contract.md`.

The aim, as in gorefactor, is to show whether refactoring helps AI-driven
development: Claude chooses refactorings, the deterministic tools perform them,
and Claude then makes the small change the story needs. Judge work by whether it
helps Claude do that during real feature work, and by whether working through
the tools costs fewer tokens than editing by hand. Once the server runs, aim for
about 95% of coding through the tools, this project's own code included.

The MCP server is `Server` (STDIO); the tools are defined in `Tools.ALL`. Every
change goes through the type-check gate (`Gate`, `TypeCheck`), which first
declares stubs for what an edit uses and nothing declares (`Stubs`), and is
recorded in `Journal`. Check the roadmap in `docs/design.md` before starting
work.

## How we work

TDD, in this order; refactor only while every test passes:
1. Green: make the change easy. If the change would be hard, refactor first,
   before writing the test, until it is small and local.
2. Red: write the next test (a golden case, or a unit test) and see it fail.
   If the test needs code that does not exist, it needs a compiling stub, so
   the build and the gate still run; the edit tools declare one for you.
3. Green: make it pass with the least code, even code that duplicates what is
   there.
4. Green: refactor, removing that duplication and shaping the code for the
   changes we will want next. Rerun the tests.

When a test is failing or does not compile, the next step is code that makes it
pass, not a refactoring. If you find you need one, undo the test (or set it
aside), refactor while green, and add the test again.

One branch and one PR per item, never commits to `main`. Merge your own PR
once CI is green (see the session notes). When the user says "do X, then Y",
do them in order, each in its own PR. Before pushing, run what CI runs,
`./mvnw verify` (see "Build and test").

Each PR also updates the docs it affects (see "Documentation to keep in sync"):
mark a roadmap item *Done* when it is, and fix text that still calls finished
work open. Do not write a PR number into docs before the PR exists.

Once the server runs, use it for every refactoring and edit it can do. When no
tool covers a change, make it by hand and log it in `docs/friction-log.md`. That
log is the evidence for which tool to build next. Before deciding that no tool
covers a change, name what the result is made of in operations (extract, fold,
inline, ...) and look for a sequence of two or three tools that composes to it.
A friction-log entry that says no tool does something names the sequences
tried and why each failed.

When a rule could reasonably go more than one way, set out two or three options
with what each costs, let the user choose, and record the choice with the
options it beat (in `docs/design.md` or the session notes).

Ask before starting an A/B run unless the user asked for one: each costs money.

Match the surrounding code: its comment density, naming and idiom. Doc comments
say what a thing is or does, plainly.

What earlier sessions learned (the user's preferences, why past choices were
made, where the work stands) is in `docs/session-notes.md`, imported here. Keep
it current: add what a later session would need, in the same PR as the work,
and fix or remove what has become wrong. Notes saved anywhere else (memory, a
claude.ai Project) do not reach sessions on other machines.

@docs/session-notes.md

When the user asks "what's next?", use the `whats-next` skill: it keeps a
record of the chosen item in `docs/sessions/<branch>.md`, and
`bin/session-report` reports the branch's tool calls from the session log.

## Using java-refactor-mcp

`.mcp.json` starts `bin/jrefactor-mcp`, which serves this checkout, rebuilding
the jar first when `pom.xml` or `src/main` changed. A session's tools are the
build from when the server started: test a server change with `./mvnw test`,
not by calling the tool, and after merging, check out `main` and reconnect the
server (`/mcp`, jrefactor, Reconnect).

1. **Load every schema at once:** ToolSearch `+jrefactor` with `max_results`
   50. After a reconnect the client may list older descriptions; the server's
   own are current.
2. **Read Java through `inspect`:** the file without method bodies, then the
   line ranges you will change. Each line is the number, two spaces, then the
   code: do not count those two spaces as indentation in `old`. To read a
   declaration wherever it is, `inspect` with `names` (`["Fold.leave"]`, `["Cart"]`);
   to find a line or a use, with `pattern` (a regular expression; without `file`,
   the whole project), not grep. Avoid Read or `cat` of whole `.java` files; each
   result is sent again on every later turn.
3. **Prefer a refactoring to an edit.** Renames, signature changes, extract and
   inline, and the moves carry a change to every use and type-check it; each
   tool's description says what follows. When a line has two meanings, `name`
   picks one: `Counter.count` or `local count`.
4. **Write through the edits:** `create_file` for new files (tests, fixtures),
   `add_declaration` for new members (by `type`; placed by kind),
   `replace_declaration` for a whole method, field or type by name (empty
   content deletes), `replace_text` for a few lines, literals, comments and
   non-Java files (`append` adds at the end of a file: a friction-log entry). Imports are added and removed for you; pass `imports` when a
   refusal says a name is ambiguous. Test first: write the test with
   `add_declaration` or `create_file`; the project's methods, constructors and
   classes it calls that nothing declares get "not implemented" stubs, typed
   from the use, and `add_declaration` of the real one replaces the stub.
5. **Plan from one read, send one batch.** Dependent steps (a signature, then
   its callers) go in one `batch` with check `"end"`; lines as you read them
   before the batch. `undo` reverts the last call or batch.
6. **Read refusals; they say what to send.** A refusal changes nothing.

**Without the MCP server** (a container that cannot run it, a script):
`bin/jrefactor` runs the same tools from a shell, one call per run, with the
same arguments and results: `bin/jrefactor list`, `bin/jrefactor rename
file=... line=7 name=t new_name=total`, a JSON object (`'{...}'`, `@file`, or
`-` for stdin; alone, its `"tool"` key names the tool), and `content_file=path`
(any `key_file`) for long text, or `content_file=-` to pass it from a heredoc
(one argument per call), without a script or a scratch file. Code for a JSON
object (a `batch`) goes after it, unescaped, in sections: a line `@@name` starts
one, and the JSON string `"@name"` is its text, its last newline dropped.
Exit status 0 done, 2 refused, 1 error. `bin/jrefactor undo` reverts the last
call, also one from an earlier run. Send several steps as one `batch`. The jar
is rebuilt when a commit changes `pom.xml` or `src/main`, not for uncommitted
edits; `bin/jrefactor --rebuild ...` (or `JREFACTOR_REBUILD=1`) builds from the
working tree first, to probe server code before a commit.

## Build and test

```sh
./mvnw verify          # compile (-Xlint:all -Werror), run unit and golden tests, check formatting
./mvnw -q test         # quieter, tests only
./mvnw spotless:apply  # format sources (google-java-format); hand edits are not formatted
```

`./mvnw package` (and `verify`) also builds `target/java-refactor-mcp.jar`, the
server: `java -jar target/java-refactor-mcp.jar <project root>`. CI runs
`./mvnw verify` on JDK 21 and `spotless:check` on every push to `main` and every
pull request.

- JDK 21 (a full JDK with `javac`, not only the JRE). Maven comes from the
  wrapper (`.mvn/wrapper/maven-wrapper.properties`). Set `JAVA_HOME` to the
  JDK 21 if the default `java` is another version.
- Versions are pinned in `pom.xml`; note a bump in `docs/session-notes.md`.
- In the agent sandbox, Maven does not use the egress proxy by itself, so it fails
  to resolve `repo.maven.apache.org` by name. Fix: a `~/.m2/settings.xml` with an
  active `https` proxy at the host and port in `$HTTPS_PROXY` (the port changes
  between sessions, so regenerate the file each time). No mirror is needed.

## Working rules

- **Coordinates are 1-based** for lines, matching gorefactor and `inspect`-style
  outlines. Columns are 1-based too unless the contract says otherwise.
- **Every refactoring type-checks before and after.** A change that introduces a
  compile error is rolled back and reported, never left applied.
- **Refusals are model-facing.** Each refusal names the reason and the fix the
  model should try next, in plain words. Refusal wording is part of the contract.
  A tool's description says what the tool does, not what it refuses: the refusal
  explains itself when it happens (the user, 2026-11-06). `docs/tool-contract.md`
  keeps the list of each tool's refusals.
- **Descriptions do not repeat what every tool shares.** Type-checking, stubs,
  imports and how refusals work are said once, in the server's instructions
  (`Server.INSTRUCTIONS`); a description gives the tool's own behavior, its
  arguments' meaning, and an example.
- **Golden cases first.** Add `src/test/resources/golden/<case>/{in,out}` (plus
  `args.properties` or `refused.txt`) before or with the code that makes it pass.
  `GoldenTest` runs every case; `refused.txt` holds a fragment the refusal must contain.
  A refactoring that moves code also gets behavior probes under
  `src/test/resources/probes` (`ProbeTest` runs the program before and after).
- **No JDT UI or LTK.** Use JDT Core directly. Do not depend on the Eclipse
  workbench, OSGi runtime or a workspace on disk.
- **Do not reprint the AST.** Edits are text edits against the original source,
  so comments and formatting survive.
- **Small results.** Results report what changed, not the whole file. Measure
  before adding fields.
- **Prefer mechanisms to guidance.** In gorefactor, coaching tips and longer
  descriptions changed nothing measurable; checking arguments up front,
  suggesting ranges in refusals and declaring stubs did.

## Documentation to keep in sync

- `docs/tool-contract.md`: update when a tool name, argument or result shape changes.
- `docs/design.md`: the roadmap, and each decision that changes the architecture.
- `docs/session-notes.md`: current facts a later session needs; fix stale ones.
- `docs/how-this-project-was-built.md`: the arc of the project and each decision that
  changed how work is done (as gorefactor's of the same name); extend it in that PR.
- `docs/friction-log.md`: each time a tool got in the way or a refactoring was
  done by hand. Append entries at the end of the file, so parallel PRs do not
  conflict at the same place. The roadmap is ordered by this log, as in gorefactor.

## Things not to do

- Do not add a dependency on Eclipse UI, LTK or the workbench.
- Do not write into the user's source tree from a query-only tool.
- Do not add tools speculatively. Add one when the friction log or a task needs
  it, and prefer an option on an existing tool to a new one.
- Do not commit generated files, `target/`, or IDE metadata.

## Shared with gorefactor

The tool contract and the A/B measurement harness are meant to be shared with
gorefactor. Until that happens, changes to shared behavior go into
`docs/tool-contract.md` first, then into both codebases. gorefactor's
`docs/how-this-project-was-built.md` records which of its practices paid off;
read it before adopting or dropping one.
