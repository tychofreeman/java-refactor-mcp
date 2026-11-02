# Design

Status: draft, 2026-11-02. Decided items are marked **Decided**. Everything else
is open.

## Context

gorefactor (Go) gives Claude refactorings that type-check, roll back on failure
and can be batched. Its measurements show that results must be small, dependent
steps should be batched, and the server should do what the model would get
wrong. This project carries those lessons to Java.

## Decisions

**Decided: JDT Core, headless.** The engine is `org.eclipse.jdt:org.eclipse.jdt.core`
used without the Eclipse workbench. It gives a compiler, AST, bindings, search,
and `ASTRewrite`. Not chosen: OpenRewrite (batch recipes, not line-driven edits),
JavaParser (weaker type resolution), and IntelliJ PSI (hard to host outside the IDE).

**Decided: text edits applied by the server.** Each refactoring computes text
edits against the original source, applies them to disk, type-checks before and
after, and rolls back on failure. This is the gorefactor model. JavaLens returns
edits for the model to apply; that is the main reason this project does not
extend it. See the conversation notes from 2026-11-02.

**Decided: MCP over STDIO with the official Java SDK.** The dependency is
`io.modelcontextprotocol.sdk:mcp`, which is `mcp-core` plus its Jackson 3 JSON
binding; `mcp-core` alone has no JSON mapper and fails at startup. The server is
one shaded jar (`target/java-refactor-mcp.jar`) serving one project root.
Tool schemas leave out `"additionalProperties": false`: with it the SDK refuses
an unknown argument before the tool runs, without naming the arguments to use;
the tool's own check names them.

**Decided: 1-based lines** in every tool argument and result, matching gorefactor
and `inspect`-style outlines.

**Decided: Maven.** Group `io.github.tychofreeman`, artifact `java-refactor-mcp`,
JDK 21.

**Decided: separate repos** for the Go and Java servers. A shared repo for the
tool contract and the A/B harness is planned once a second language exists.

## Open questions

1. ~~**Running JDT Core without OSGi.**~~ Answered by the spike in
   `spikes/jdt-headless/` (2026-11-02). Headless `ASTParser` with
   `setResolveBindings(true)`, `setBindingsRecovery(true)` and
   `setEnvironment(classpath, sourcepath, encodings, true)` works with no
   workspace and no OSGi runtime started:
   - Types from other files on the source path resolve, as do JDK types.
   - Library jars resolve only when on the classpath. Without them, the imports
     and their uses are reported as problems and the calls come back unresolved.
   - The unit still parses and the rest of its bindings resolve; missing types
     are recovered, not fatal.
   - No Eclipse platform warnings were printed.
   - Caveat: `jdt.core` 3.47.0 pulls about 30 transitive jars (`org.eclipse.core.*`,
     OSGi, equinox, jna). They are needed on the classpath, but the spike did not
     test whether the whole set is required. Trim later if it matters.
   Still open: the after-edit type check (re-parse the edited sources and compare
   problems), timing on large projects, and deriving the classpath from Maven and
   Gradle.
2. **Project loading.** Options: read the Maven and Gradle models through the
   build tool (as JavaLens does), or parse `pom.xml` and the classpath directly.
   Start with a plain classpath passed in by the caller; add build-tool loading
   later.
3. **Undo.** Keep original file contents in memory for the life of the server
   session, or write backups under the workspace. Decide with the `batch`
   design.
4. **Batching.** Mirror gorefactor's `batch` with `$prev.*` references. Confirm
   that line-following through edits works the same way with JDT's positions.
5. ~~**Versions.**~~ Resolved 2026-11-02: JDT Core 3.47.0, MCP SDK 2.1.0 and JUnit
   5.14.4 resolve from Maven Central and `mvn verify` passes on the scaffold.

## First milestone (proposed)

1. ~~JDT spike~~ Done (`spikes/jdt-headless/`).
2. Type-check gate and first refactoring. **Done in code, 2026-11-02:**
   `TypeCheck` counts compile errors across the project before and after an edit;
   `RenameLocal` applies the edit, runs the gate, and rolls back with a refusal if
   the edit adds errors. Golden cases pass. Fields and other kinds of rename are
   not built.
3. ~~Start the MCP server over STDIO with one tool~~ Done (roadmap #5): `inspect`
   lists and answers from Claude Code.
4. ~~Wire `rename_local` into the server~~ Done (roadmap #6).
5. Run the A/B harness on a small Java task set against a stock baseline.

## Roadmap

Ordered by what unblocks the next step. Status as of 2026-11-02. When a tool
or item is done, change its status here and in `docs/session-notes.md`.

| # | Item | Status | Notes |
|---|------|--------|-------|
| 1 | JDT spike: headless bindings | Done | `spikes/jdt-headless/`; open question 1 |
| 2 | Type-check gate | Done | `TypeCheck`; see "Gate" below |
| 3 | `rename_local` (locals and parameters) | Done | Four golden cases |
| 4 | CI: tests, formatting, compiler lint; Dependabot | Done | `.github/`; pom Spotless and `-Xlint:all -Werror`; first run green 2026-11-02 |
| 5 | MCP server over STDIO, one read-only tool (`inspect`-style outline, 1-based lines) | Done | `Server`, `Inspect`; record in `docs/sessions/mcp-server-inspect.md` |
| 6 | Wire `rename_local` into the server, same refusal wording | Done | Addressed by name as in gorefactor's `rename`; record in `docs/sessions/rename-local-tool.md` |
| 7 | Project loading from Maven and Gradle (classpath from the build tool) | Open | Open question 2; today the caller passes jars |
| 8 | Undo and `batch`, with `$prev.*` references | Open | Open questions 3 and 4 |
| 9 | Field rename, and other kinds of rename | Planned | Crosses files; needs an approach for references across the project |
| 10 | Measure gate cost on a large project; cache or narrow the reparse if needed | Open | The gate reparses every file per edit. First point (2026-11-02): a `rename_local` on 390 files, no classpath, took about 5 s |
| 11 | A/B harness run on a small Java task set against a stock baseline | Planned | Needs items 5 and 6 |
| 12 | Trim the transitive JDT jar set | Open, low priority | Caveat in open question 1 |
| 13 | Build this project through its own tools: `.mcp.json` running this checkout; hand edits logged | Done | `bin/jrefactor-mcp`; record in `docs/sessions/mcp-json-self.md` |
| 14 | Golden cases also undo each applied step and compare with `in/`; compare incremental state with a fresh load once there is any | Planned | After 8; from gorefactor, where it caught reloader bugs |
| 15 | Read guard: a hook that sends reads of `.java` files through `inspect` | Planned | After 5; in gorefactor a hook changed reading where instructions did not |
| 16 | Opt-in stress tests on a real project for tools whose rules are heuristics, with a counts-only mode | Open | From gorefactor; compare versions on identical input |
| 17 | A test that holds the tool count and the instructions' size, raised only with the user | Open | From gorefactor's tool budget |
| 18 | Session log: a hook that records every tool call per branch, and a report of it for the session records | Planned | After 5; gorefactor's `cmd/session-log` and `refactor-tokens -log`; the `whats-next` skill uses it |

## Gate: how the type check works

Edits are written to disk, the whole project is parsed again, and the new
compile errors are compared with the old ones by file and message (not line,
so moving code does not count). Any new error means the file is restored and the
edit refused. The gate reparses every file on each call, which is slow on large
projects; that is accepted until measured.
