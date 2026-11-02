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
2. ~~**Project loading.**~~ Decided 2026-11-02 (roadmap #7): a `pom.xml` at the
   root means source roots `src/main/java` and `src/test/java`, and the test
   classpath from Maven itself (`maven-dependency-plugin:3.11.0:build-classpath`,
   pinned, run by the project's `./mvnw` or `mvn`, its output kept off stdout).
   Not chosen: parsing `pom.xml` by hand (no transitive dependencies) and
   embedding Maven Resolver (a large dependency for one call). No build file, or
   Maven failing, falls back to the root as the one source root with no
   classpath, and says so on stderr. Still open: Gradle, multi-module builds,
   non-standard source directories, and reloading when `pom.xml` changes.
3. ~~**Undo.**~~ Decided 2026-11-02 (roadmap #8), as gorefactor: in memory per
   server process (`Journal`), one entry per top-level call or batch. Not chosen:
   backups under the workspace (they would be written into the user's tree).
4. ~~**Batching.**~~ Decided 2026-11-02 (roadmap #8), as gorefactor. Lines are
   followed by a line diff of each step's file (common first and last lines), not
   by JDT positions: every tool's writes are whole-file texts.
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

Item numbers are stable (records and notes refer to them); the order of the
open items is the order of the second table. Status as of 2026-11-02. When an
item is done, change its status here and in `docs/session-notes.md`.

### Done

| # | Item | Notes |
|---|------|-------|
| 1 | JDT spike: headless bindings | `spikes/jdt-headless/`; open question 1 |
| 2 | Type-check gate | `TypeCheck`; see "Gate" below |
| 3 | `rename_local` (locals and parameters) | Four golden cases |
| 4 | CI: tests, formatting, compiler lint; Dependabot | `.github/`; pom Spotless and `-Xlint:all -Werror`; first run green 2026-11-02 |
| 5 | MCP server over STDIO, one read-only tool (`inspect`-style outline, 1-based lines) | `Server`, `Inspect`; record in `docs/sessions/mcp-server-inspect.md` |
| 6 | Wire `rename_local` into the server, same refusal wording | Addressed by name as in gorefactor's `rename`; record in `docs/sessions/rename-local-tool.md` |
| 13 | Build this project through its own tools: `.mcp.json` running this checkout | `bin/jrefactor-mcp`; record in `docs/sessions/mcp-json-self.md` |

### Open, in order

Ordered by gorefactor's evidence (see "Why this order" below) and this repo's
friction log; reorder when this repo's own log says otherwise.

| Order | # | Item | Status | Evidence |
|---|---|------|--------|----------|
| 1 | 7 | Source roots (main and test) and the classpath, from Maven first (Gradle later) | Done | The gate cannot resolve JDT or MCP types on this repo, so it cannot yet stand behind edits. TS spike: a rename that loaded one project missed three test files |
| 2 | 19 | First edits: `create_file`, `replace_text` (any file), `add_declaration` (a member into a type, replacing a stub of it), `replace_declaration` (by `Type.member`, empty content deletes) | Done | gorefactor's share of edits through the tools went from 10-20% (Oct 17) to 80-99% the day its edits came (cost history). All three of this repo's friction entries are hand edits these cover |
| 3 | 20 | Imports added and removed by every edit, not by a tool | Done | 52 gorefactor entries mention imports; "2026-10-20: imports fixed after every edit". The TypeScript port did the same |
| 4 | 8 | Undo, and `batch` with check `"end"`, lines translated through earlier steps, and `$prev.*` references | Done | Oct 19 in gorefactor: 99-100% through the tools and input tokens per step from 0.5-1.8M to 170-190k. "2026-10-18: a batch checked at its end" ("hit four times"). Open questions 3 and 4 |
| 5 | 21 | Stubs from usage (`create_from_usage`): a test written first gets compiling stubs | Next | "2026-10-17: adding `remove_parameter`" ("A red test blocks every refactoring"); first in TypeScript's gap list (item 41), second in item 43. CLAUDE.md's TDD order needs it |
| 6 | 22 | Signature changes: `add_parameter`, `remove_parameter`, `move_parameter`, across overrides and interface implementations | Planned | gorefactor roadmap item 1, "the most common by-hand step"; "2026-10-20: signature tools change interface families". This repo's log names `add_parameter` (2026-11-02) |
| 7 | 23 | `extract_function` (Java: extract method) | Planned | This repo's log names it twice (`GoldenTest.call`, `atColumn`) |
| 8 | 18 | Session log: a hook that records every tool call per branch, and a report of it for the session records | Planned | Without it the records cannot say "measured"; gorefactor's `cmd/session-log` and `refactor-tokens -log` |
| 9 | 24 | Results that do not echo what the model sent; locations, not diffs | Planned | gorefactor's cost history: smaller results were "the largest single win". Build into each edit as it comes; this item audits them once there are several |
| 10 | 9 | Field, method and type rename across files; `rename_local` also updates Javadoc `@param` | Planned | 20 gorefactor entries on renames leaving doc comments stale ("Seven renames, seven stale doc comments") |
| 11 | 10 | Measure gate cost on a large project; cache or narrow the reparse | Open | First point (2026-11-02): a `rename_local` on 390 files took about 5 s. In gorefactor, reloads that stopped compiling the module took a 13-step batch from 546 s to 21 s |
| 12 | 14 | Golden cases also undo each applied step and compare with `in/`; incremental state compared with a fresh load | Planned | After 8; in gorefactor this caught reloader bugs |
| 13 | 11 | A/B harness run on a small Java task set against a stock baseline | Planned | Needs edits, batch and stubs to be a fair test; gorefactor's fresh-model arm cost about twice the manual arm until they existed |
| 14 | 16 | Opt-in stress tests on a real project for tools whose rules are heuristics, with a counts-only mode | Open | From gorefactor; compare versions on identical input |
| 15 | 17 | A test that holds the tool count and the instructions' size, raised only with the user | Open | gorefactor's tool budget |
| 16 | 15 | Read guard: a hook that sends reads of `.java` files through `inspect` | Planned | Last: in gorefactor's A/B the guarded arm did worse ($2.95, 5/8 against $2.71, 6/8) |
| 17 | 12 | Trim the transitive JDT jar set | Open, low priority | Caveat in open question 1 |

### Why this order

From a survey of gorefactor's `docs/friction-log.md` (434 entries, 15 Oct to
2 Nov), its cost history, roadmap and TypeScript notes, checked on 2026-11-02:

- The log's counts are mentions, not gaps: after Oct 19 most entries say the
  work went through the tools, so a tool built early is mentioned because it
  was used. The order follows what changed the share of edits made through the
  tools and the cost per step (cost history, section 5), and the entries that
  name a gap, not raw counts.
- Edits first, then batching: the two jumps in the share of work done through
  the tools came the day the edit tools came (Oct 18) and the day batch, check
  `"end"` and references came (Oct 19).
- Loading before edits: the TypeScript spike's first finding was that a rename
  over one loaded project missed test files, and was caught only by the gate.
  This repo's gate cannot resolve its own dependencies yet.
- Mechanisms over guidance: smaller results, checked arguments, suggested
  ranges and stubs helped fresh models; more tools, longer descriptions, a
  coaching tip and the read guard did not.
- Gaps that were compositions of existing tools (moving a field, the 34 calls
  of `set`) are listed in gorefactor's `docs/derivations.md`; check there before
  adding a tool.

## Gate: how the type check works

Edits are written to disk, the whole project is parsed again, and the new
compile errors are compared with the old ones by file and message (not line,
so moving code does not count). Any new error means the file is restored and the
edit refused. The gate reparses every file on each call, which is slow on large
projects; that is accepted until measured.
