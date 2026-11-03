# CLAUDE.md

Instructions for Claude Code in this repository. Read this first.

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

Current state: early. The MCP server (`Server`, STDIO; tools defined in
`Tools`) exposes `inspect` (read-only), `rename_local`, and four edits:
`create_file`, `replace_text`, `add_declaration`, `replace_declaration`; `batch`
and `undo`. Every change goes through the type-check gate (`Gate`, `TypeCheck`),
which first declares stubs for what the change uses and nothing declares (`Stubs`),
and is recorded in `Journal`. Check the roadmap and open questions in
`docs/design.md` before starting work.

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
once CI is green (see the session notes). When
the user says "do X, then Y", do them in order, each in its own PR. Before
pushing, run what CI runs (`.github/workflows/ci.yml`):

    ./mvnw verify   # compiles with -Xlint:all -Werror, runs the tests, checks Spotless

Hand edits are not formatted, so run `./mvnw spotless:apply` first if `verify`
reports formatting.

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

When the user asks "what's next?", the `whats-next` skill
(`.claude/skills/whats-next`) offers the next items, and keeps a record of the
chosen one in `docs/sessions/<branch>.md`: the plan before code, and notes of
hand edits, refusals, reads and large writes.

## Using java-refactor-mcp

`.mcp.json` starts `bin/jrefactor-mcp`, which serves this checkout with the
server built from it, rebuilding the jar first when `pom.xml` or `src/main`
changed. Claude Code asks once to approve the project's server. A session's
tools are the build from when the server started: after changing the server,
test the change with `./mvnw test`, not by calling the tool. To use it once
merged, check out `main` and reconnect the server (`/mcp`, jrefactor,
Reconnect); no restart of Claude Code is needed. A reconnect serves whatever is
checked out, a branch's build included.

1. **Load every schema at once:** `select:mcp__jrefactor__inspect,mcp__jrefactor__rename_local,mcp__jrefactor__create_file,mcp__jrefactor__replace_text,mcp__jrefactor__add_declaration,mcp__jrefactor__replace_declaration,mcp__jrefactor__batch,mcp__jrefactor__undo`.
   After a restart or reconnect the client may list descriptions from before it; the server's
   own are current (it accepts the arguments they name).
2. **Read Java through `inspect`:** the file without method bodies, then the
   line ranges you will change. Each line is the number, two spaces, then the
   code: do not count those two spaces as indentation in `old`. Avoid Read or `cat` of whole `.java` files;
   each result is sent again on every later turn.
3. **Rename locals and parameters with `rename_local`,** by `file`, `line` and
   `name`; it finds every use and type-checks.
4. **Write through the edits:** `create_file` for new files (tests, fixtures),
   `add_declaration` for new members (by `type`, `after`/`before`),
   `replace_declaration` for a whole method, field or type by name (empty
   content deletes), `replace_text` for a few lines, literals, comments and
   non-Java files. Imports are added and removed for you; pass `imports` when a
   refusal says a name is ambiguous. Test first: write the test with
   `add_declaration` or `create_file`; the project's methods, constructors and
   classes it calls that nothing declares get "not implemented" stubs, typed
   from the use, and `add_declaration` of the real one replaces the stub.
5. **Plan from one read, send one batch.** Dependent steps (a signature, then
   its callers) go in one `batch` with check `"end"`; lines as you read them
   before the batch. `undo` reverts the last call or batch.
6. **Read refusals; they say what to send.** A refusal changes nothing.

Edits the tools cannot make are by hand: log each in `docs/friction-log.md`,
naming the tool or composition that would have done it.

## Build and test

```sh
./mvnw verify          # compile (-Xlint:all -Werror), run unit and golden tests, check formatting
./mvnw -q test         # quieter, tests only
./mvnw spotless:apply  # format sources (google-java-format, GOOGLE style); run before committing
```

`./mvnw package` (and `verify`) also builds `target/java-refactor-mcp.jar`, the
server: `java -jar target/java-refactor-mcp.jar <project root>`.

CI (`.github/workflows/ci.yml`) runs `./mvnw verify` on JDK 21 and `./mvnw spotless:check`
on every push to `main` and every pull request. Dependabot (`.github/dependabot.yml`)
opens weekly PRs for Maven dependencies and GitHub Actions versions.

- JDK 21 (a full JDK with `javac`, not only the JRE). Maven comes from the
  wrapper: `./mvnw` downloads the Maven pinned in `.mvn/wrapper/maven-wrapper.properties`
  (3.9.16, with its SHA-256) into `~/.m2/wrapper` on first run. Set `JAVA_HOME`
  to the JDK 21 if the default `java` is another version.
- Pinned versions (checked against Maven Central on 2026-11-02): JDT Core 3.47.0,
  MCP SDK 2.1.0, JUnit 6.1.3 (Dependabot, merged 2026-11-02). Bump them in
  `pom.xml` and note the change in `docs/session-notes.md`.
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
  Change a tool's description in the PR that changes what it refuses.
- **Golden cases first.** Add `src/test/resources/golden/<case>/{in,out}` (plus
  `args.properties` or `refused.txt`) before or with the code that makes it pass.
  `GoldenTest` runs every case; `refused.txt` holds a fragment the refusal must contain.
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
