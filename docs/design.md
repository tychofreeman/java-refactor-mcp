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

**Superseded the same day (the user, 2026-11-05): the read guard denies every grep of a
`.java` file again, and `inspect` has `pattern` (#202) and `names` (#203) for the searches the
relaxation below allowed; the hidden-path refusal stays. History in
`docs/how-this-project-was-built.md`.** Was: **the read guard denies a grep that shows Java text,
not one that shows none (the user, 2026-11-05).** A `grep` of a `.java` file stays denied, as in gorefactor's guard, when it prints
lines; one with `-c`, `-l`, `-L` (grep) or `-q`, which prints a count, names or nothing, is
allowed, and a path a shell variable hides is refused saying to write it out. Options it beat:
allowing `grep -n` of Java too (cheap line search, but against gorefactor's rule), and a
pattern search in `inspect` that the refusal points to (more work).

**Decided: change_type of or to a primitive refuses the uses the type decides (the user,
2026-11-05).** Probes found `int` to `long`, `int` to `double` and `char` to `int` compile
and change overflow, division and printing. A use that is an operand of arithmetic, a
shift, a unary or compound operator, an argument of a call, a cast, a branch of `?:`, a
switch selector, or `==` on a boxed type is refused (`PrimitiveUses`); copies, returns and
ordering comparisons are allowed. Options it beat: refusing every primitive type change
(simplest, but `int` to `long` could no longer be done by the tool), and applying it while
listing the risky uses in the result (most permissive, but a normal-flow change would be
made knowingly, against the policy above).

**Decided: the behavior gate checks declared moves (roadmap #96, the user, 2026-11-05).**
Each refactoring returns, with its edits, what it does to code: a piece moved
(and what it crosses, and whether it lands where it runs only sometimes or
later), duplicated or dropped. One checker derives the effect-order
(`Effects`), same-meaning (`NameCheck`) and exceptional (`Exceptional`) checks
from that, so a tool states facts and the rules live in one place. Migrated
tool by tool; a tool's one-off rules stay only where their refusal is clearer.
Options it beat: inferring the moves from a diff of the trees (covers every tool
at once, but inlining substitutes and renames change text, so matching is
heuristic and would refuse falsely), and keeping the opt-in with a test that
every refactoring calls the three checks (cheapest, but the rules stay
scattered and `InlineFunction` does not shrink).

**Decided 2026-11-05 (the user): a release on every push to main that changes code and
passes CI.** The `release` job of `ci.yml` tags the commit with the next version after the
latest tag, bumped by the merged pull request's label (`major`, `minor`, else the patch),
builds the jar with that version in its manifest (`-Drelease.version`, which
`Server.version()` reports; any other build reports `devel`), and creates the GitHub
release with generated notes and the jar attached. Not chosen: a version raised by hand in
a PR (gorefactor's first scheme, which let main run 53 PRs past its release), a date or a
patch-only bump, and committing the bump back to `main`. The first release is v0.1.0.

**Decided: separate repos** for the Go and Java servers. A shared repo for the
tool contract and the A/B harness is planned once a second language exists.

**Decided 2026-11-03: stubs travel in the call's result, not a side channel.**
An edit's declared stubs are returned in the result object of its call. Today
`Stubs` collects them in a `ThreadLocal` and `Tools.call` appends them to the
text, so a handler's output depends on state it does not return. The user chose
the result object. Not chosen: keep the side channel (the option it beats),
which needs no change to handlers but hides their output from tests and from
any caller that is not `Tools.call`.

**Decided 2026-11-04: effects in normal flow are preserved; exceptional behavior is not, by default.**

This extends the definition of refactoring as a behavior-preserving transformation. A
transformation cannot preserve every kind of behavior, so a refactoring is defined by the kinds it
keeps, and choosing those kinds is part of the design. Normal logic flow is the kind kept by
default. Exceptional behavior is kept only where a guard or a try/catch makes it part of the normal
logic; elsewhere it is a concession, made knowingly and reported.
A refactoring keeps every effect that is part of the normal logic flow: calls, assignments and
reads, and their order. Exceptional behavior (an exception thrown, a null dereference, an array
index failing, anything outside the normal flow) is not preserved by default, with two exceptions
that are kept without a flag:
- a guard: a null check or condition that stops the exception before it happens (`if (x != null)
  x.f()`), since the guard is part of the normal logic;
- a try/catch: the source of an exception that is caught stays inside the `try` block.

The model can require exceptional behavior to be preserved with a flag, for a tool that can
change it. A tool that detects a change in exceptional behavior it did not preserve says so to the
model in its result, so the model can decide whether the change is acceptable. Not chosen: always
preserving exceptional behavior (it would refuse most moves that pass the normal-flow rule, such
as an inlined initializer that may throw) and ignoring it (the model would not know).

**Decided 2026-11-05: an edit chooses an ambiguous import on evidence (the user, roadmap #105).**
When a simple name could be several classes, the import fix takes the one the file imported
before the call or its batch, else the only one in a package the file imports from, and names the
choice in the result ("chose java.util.Date over java.sql.Date: the file imports from java.util").
Without such evidence it refuses once, listing every ambiguous name, with a `java.*` candidate
suggested first. Options it beat: choosing only a prior import (fewer guesses, but the
`org.eclipse.jdt.core.dom` family case still refused), and never choosing (one refusal for all
names, but still a resend). The type check stays behind every choice.

## Constructive transformations (2026-11-05)

The thesis widened (the user, 2026-11-05): not only that refactoring lets a model think at a
higher level, but that transformations as a whole do. In a good IDE a programmer types only
sometimes, and then thinks about structures, not letters: the class, its constructor, the stubs an
interface asks for, a test method's shell and the `try` around a call come from commands; what is
typed is the idea, an expression or a few statements. Here `create_file` was the one place with no
such commands, so a model typed whole classes through it: about 5,600 lines of `src/main` and
2,000 of `src/test` since the session log began, the largest a 592-line tool in one call.

The vocabulary, by level, and what it has:

| Level | IDE move | Here | Missing |
|---|---|---|---|
| File and type | new class, interface or record in a package, with supertypes | `create_file` (whole text) | a type by its declaration, in the right source root |
| Members from structure | implement or override methods, generate a constructor, accessors, `equals`/`hashCode`, delegates | `encapsulate_field`; stubs from usage | implement/override; constructor from fields |
| Members from usage | create a method from a call | stubs, automatically | |
| Tests | create a test for a class or method | nothing | a test class, and a test method that calls the target |
| Statements | surround with if, for or try; introduce a variable; add a case or element | `insert_statements`, `replace_statements`, `extract_variable`, `add_case`, `add_element` | surround with |
| Expressions | typed | `replace_expression` | (typing belongs here) |

What is measured changes with it: beside the share of edits made through the tools, the share of
typed text that is statements and expressions rather than structure. The A/B harness can test the
thesis on feature tasks (gorefactor's `ab-features`, ported), an arm with skeleton-only creation and
the constructive tools against the current tools.

**The first slice (the user, 2026-11-05), one PR each, in this order:**

1. `create_file` with `declaration` in place of `content` (roadmap #114): the package line and the
   declaration (`class Cart implements Priced`, `record Line(int qty)`), with an empty body; with
   supertypes, a stub for each abstract method they leave unimplemented. The file's path follows
   from the package and the type's name, in the main source root unless `test` is set.
2. A test from its target (roadmap #115): `create_file` with `test_for` (`Cart`) writes the empty
   test class in the test root's mirrored package; `add_declaration` with `test_for`
   (`Cart.total`) adds a test method that builds the receiver and calls the method, ending in
   `fail(...)`, so it is red until the model types the assertion.
3. Skeleton-only creation, an experiment behind a flag (roadmap #116): a Java `create_file` with
   member declarations in its content is refused, naming the operations above. Off by default, as
   the write guard started; the A/B arm turns it on.

Choices in the slice and the options they beat (recorded as defaults; reopen with evidence):

- **Options on existing tools, not new ones** (CLAUDE.md's rule): `declaration` and `test_for` on
  `create_file`, `test_for` on `add_declaration`. Beat: a `create_type` and a `create_test` tool,
  clearer names but two more tools to describe, against the evidence that fewer, wider tools served
  gorefactor's models.
- **A generated test is red** (`fail("write the assertion")`): test-first, as CLAUDE.md works.
  Beat: an empty body that passes (a green test that tests nothing), and a guessed assertion on the
  result (wrong more often than not).
- **The test framework from the classpath:** JUnit Jupiter when it resolves, else JUnit 4, else
  refused. Beat: a framework argument (one more thing for the model to state).
- **The flag covers `create_file` only:** `add_declaration` keeps whole members, one per call, so
  the model types a member at a time and the stub, implement and test operations give the shells.
  Beat: refusing bodies in `add_declaration` too (a stricter test of the thesis, but it needs the
  member-level generators first).

Next after the slice, by what its use logs: implement/override as its own operation on an
existing type, a constructor from fields, and surround with.

**The first measurement (2026-11-05, `docs/sessions/ab-shop-tasks.md`):** on the four shop
feature tasks, Sonnet, 2 runs each, every arm passed every task; per run manual $0.107,
mcp $0.170, mcp with skeleton-only $0.217. The skeleton rule redirected typing (each
refused file became `declaration` plus one `add_declaration` of the same members: 811
typed lines against 575) and `test_for` went unused, so the slice alone does not replace
typing; the member-level operations above are what would. One run built its new type by
transformation (`extract_struct`, then `move_type`) and was stopped by a gap on records
(roadmap #118).

## Questions (all answered)

No question is open. These were open when the design was written; each says how it
was answered.

0. **How exceptional behavior is preserved and reported.** Answered 2026-11-04 by the user: one
   `preserve_exceptions` flag on every refactoring (option a's first form), and the alert as a
   separate field in the result (option b's second). Detection starts with the syntactically clear
   cases below; roadmap #73, after #71 and #72. The options as first set out: Options: (a) one flag name on every refactoring (`preserve_exceptions=true`), or one
   per kind of exceptional behavior (`preserve_null_checks`, ...); (b) the alert as text in the
   result, or as a separate field the model must read. Detection is the hard part: a tool can
   name what it moved past (a dereference, a call that may throw) without knowing whether it
   throws. The first step would be a list of the exceptional behaviors each refactoring can
   change, from the probes, and one tool (`move_member` already has a known null-receiver change)
   as the test.

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

## Roadmap

Item numbers are stable (records and notes refer to them); the order of the
open items is the order of the second table. Status as of 2026-11-03. When an
item is done, change its status here and in `docs/session-notes.md`.

### Done

| # | Item | Notes |
|---|------|-------|
| 1 | JDT spike: headless bindings | `spikes/jdt-headless/`; question 1 |
| 2 | Type-check gate | `TypeCheck`; see "Gate" below |
| 3 | `rename_local` (locals and parameters) | Four golden cases |
| 4 | CI: tests, formatting, compiler lint; Dependabot | `.github/`; pom Spotless and `-Xlint:all -Werror`; first run green 2026-11-02 |
| 5 | MCP server over STDIO, one read-only tool (`inspect`-style outline, 1-based lines) | `Server`, `Inspect`; record in `docs/sessions/mcp-server-inspect.md` |
| 6 | Wire `rename_local` into the server, same refusal wording | Addressed by name as in gorefactor's `rename`; record in `docs/sessions/rename-local-tool.md` |
| 13 | Build this project through its own tools: `.mcp.json` running this checkout | `bin/jrefactor-mcp`; record in `docs/sessions/mcp-json-self.md` |
| 9 | Field, method and type rename across files, Javadoc included: one `rename` replacing `rename_local` (the user's choice of 2026-11-03 over a second tool or widening `rename_local`); a name with two meanings on the line is refused and picked by `Counter.count`, `local count` or column (the user's choice over "the local wins") | Record in `docs/sessions/rename-across-files.md` |
| 10 | Measure gate cost on a large project; cache the reparse | Commons Math (990 files): one edit 21.8 s to 2.1 s, `add_parameter` 15.0 s to 4.1 s, by one batch parse (`Jdt.parseAll`) and reusing the last count while no file changed. Opt-in `GateCostTest`; record in `docs/sessions/gate-cost.md` |
| 24 | Results that do not echo what the model sent; locations, not diffs | Audit found no result that repeats input or a diff; the largest result is the stub list (follow-up). Record in `docs/sessions/results-without-echo.md` |

### Done from the ordered list

Rows that were in the ordered list below, in their order; the status says how each
was finished or closed, and the evidence names its record.

| Order | # | Item | Status | Evidence |
|---|---|------|--------|----------|
| 1 | 7 | Source roots (main and test) and the classpath, from Maven first (Gradle later) | Done | The gate cannot resolve JDT or MCP types on this repo, so it cannot yet stand behind edits. TS spike: a rename that loaded one project missed three test files |
| 2 | 19 | First edits: `create_file`, `replace_text` (any file), `add_declaration` (a member into a type, replacing a stub of it), `replace_declaration` (by `Type.member`, empty content deletes) | Done | gorefactor's share of edits through the tools went from 10-20% (Oct 17) to 80-99% the day its edits came (cost history). All three of this repo's friction entries are hand edits these cover |
| 3 | 20 | Imports added and removed by every edit, not by a tool | Done | 52 gorefactor entries mention imports; "2026-10-20: imports fixed after every edit". The TypeScript port did the same |
| 4 | 8 | Undo, and `batch` with check `"end"`, lines translated through earlier steps, and `$prev.*` references | Done | Oct 19 in gorefactor: 99-100% through the tools and input tokens per step from 0.5-1.8M to 170-190k. "2026-10-18: a batch checked at its end" ("hit four times"). Open questions 3 and 4 |
| 5 | 21 | Stubs from usage: a test written first gets compiling stubs (automatic in every edit; no `create_from_usage` tool, the user's choice of 2026-11-02 over the tool alone or both) | Done | "2026-10-17: adding `remove_parameter`" ("A red test blocks every refactoring"); first in TypeScript's gap list (item 41), second in item 43. CLAUDE.md's TDD order needs it |
| 6 | 22 | Signature changes: `add_parameter`, `remove_parameter`, `move_parameter`, across overrides and interface implementations | Done | gorefactor roadmap item 1, "the most common by-hand step"; "2026-10-20: signature tools change interface families". This repo's log names `add_parameter` (2026-11-02) |
| 7 | 23 | `extract_function` (Java: `extract_method`): at most one result (the user chose refusing two over a record, 2026-11-03); static exactly when the enclosing member is (chosen over "when it can be") | Done | This repo's log names it twice (`GoldenTest.call`, `atColumn`) |
| 8 | 18 | Session log: a hook that records every tool call per branch, and a report of it for the session records (`bin/session-log`, `bin/session-report`; the hook in Java from the jar, the user's choice of 2026-11-03 over shell and jq; tokens not yet) | Done | Without it the records cannot say "measured"; gorefactor's `cmd/session-log` and `refactor-tokens -log` |
| 12 | 14 | Golden cases also undo each applied step and compare with `in/`; incremental state compared with a fresh load | Done | `GoldenTest` undoes each write and checks `TypeCheck.errors` against `TypeCheck.uncached`; record in `docs/sessions/golden-undo-check.md` |
| 15 | 17 | A test that holds the tool count and the instructions' size, raised only with the user | Done (2026-11-04): the count only, limit 50 (`ToolsTest`, `docs/sessions/tool-count-limit.md`); the user raised it for ten higher-level tools | gorefactor's tool budget |
| 17 | 25 | `pull_up` and `push_down` for methods (and fields): move a member to a supertype or to its subtypes, with the type-check gate behind it | Done (2026-11-04): `pull_up` and `push_down` for methods, fields and overloads (`docs/sessions/pull-up.md`, `docs/sessions/push-down.md`, `docs/sessions/fields-pull-push.md`, `docs/sessions/overloads-pull-push.md`) | The user asked, 2026-11-03; no friction entry yet, so it waits for one. Also worth a look for gorefactor (Go has embedding, not inheritance: the nearest is moving a method between a struct and the one it embeds) and the TypeScript port |
| 19 | 26 | Path refusal in one place: `SourceProject` returns a `.java` path or refuses, instead of five checks with different wording (`Declarations`, `Extract`, `Inspect`, `Signatures`, `Edits`) | Done | Code review 2026-11-03. Refusal wording is contract: golden `refused.txt` fragments must hold |
| 20 | 27 | One line helper, `Lines`: `lineStart` and `lineEnd` (identical in `Declarations` and `Imports`), `Edits.lineOf`, and the "line N" / "lines N, M" / "lines A-B" text in `Tools`, `Rename` and `Extract` | Done | Code review 2026-11-03 |
| 21 | 28 | One ASTParser configuration: `Declarations.members` sets its own options and no environment; `Jdt` should own every parser | Done | Code review 2026-11-03. `Jdt` owns every parser now (`Jdt.parseMembers`, `options()`); record `docs/sessions/one-parser-config.md` |
| 22 | 29 | Name the two `ancestor` helpers apart: `Extract` starts at the parent, `Stubs` at the node itself | Done (2026-11-04): `Extract.ancestorAbove`, `Stubs.ancestorOrSelf`, `docs/sessions/ancestor-helper-names.md` | Code review 2026-11-03. Same name, different meaning |
| 23 | 30 | Stubs travel in the call's result object, not `Stubs.said()` and a `ThreadLocal` (decided 2026-11-03, see Decisions) | Done | `Tools.Outcome`, `Stubs.Declared`, `Stubs.Check`; record in `docs/sessions/stubs-in-result.md` |
| 24 | 31 | The before/after type check goes through `Gate`: `Batch` and `Stubs` each re-implement it | Done | Code review 2026-11-03. `Gate.checkedAtEnd` is the end-of-batch check; `Batch` calls it. `Stubs.declare` stays the declare loop. Record: `docs/sessions/gate-before-after.md` |
| 26 | 33 | Static mutable state: `Journal.DONE`, `Imports.LIBRARIES`, `Server.CALLS`, `Stubs.DECLARED`; tests in one JVM can interfere | Done | Code review 2026-11-03. Safe in the server today: one call at a time |
| 27 | 34 | One `ObjectMapper` shared by `SessionLog` and `SessionReport` | Done | Code review 2026-11-03. `SessionReport` uses `SessionLog.JSON`. Record: `docs/sessions/shared-object-mapper.md` |
| 28 | 35 | `create_file` fixes imports before the gate, as every other edit does; a new file's unimported library type is now declared as a project class (`List.java`) | Done | Full code review 2026-11-03; reproduced by `create_file_java_imports`. `createFile` now goes through `write`, which fixes imports before the gate |
| 29 | 36 | Overload parameter lists split at commas inside generics (`Map<String, Integer>` breaks), so `replace_declaration`, `add_parameter`, `remove_parameter` and `move_parameter` refuse such overloads with names the model cannot type back | Done | Full code review 2026-11-03: reading, not run. `Declarations` splits on `,` |
| 30 | 37 | `Stubs.uses` gives every file the import picks of every stub, so an ambiguous name in one file can resolve to a class another stub named | Done | Full code review 2026-11-03: reading, not run. `Imports.fix` takes the picks |
| 31 | 38 | A failed Maven classpath is only on stderr: the server runs with no classpath and the model sees only "cannot be resolved" | Done | Full code review 2026-11-03: reading, not run. `SourceProject`, `Server` |
| 32 | 39 | Edit pipeline in one place: parse, edit text, `Imports.fix`, `Gate.applyOrRefuse` is written out at six sites (`Declarations` x3, `Edits`, `Extract`, `Signatures`), and text application four ways (`Rename.apply`, `Signatures.Change.apply`, `Edits.replaceText`, `Declarations.plan`) | Closed: partly done (2026-11-03) | Full code review 2026-11-03. The import fix and the gate are one call (`Edits.gated`) at Declarations (3), Extraction and `Edits.write`; `Signatures` (per-file loop) and the four text-application ways are not done. Record: `docs/sessions/edit-pipeline.md` Text application: `TextEdit` (one record, one `apply`) is used by `Rename`, `MoveType` and `SignatureChange`; `Declarations.plan` and `Edits.replaceText` keep their own shape (see the record). Record: `docs/sessions/edit-pipeline-text.md`. Closed by the user's choice (2026-11-03) as partly done. The per-file import fix and gate in `SignatureChange` and `MoveType` was closed on 2026-11-04 as not worth merging: the two loops fix different things (`docs/sessions/import-fix-close.md`) |
| 33 | 40 | Rename: `renameField`, `renameMethod` and `renameType` repeat the same read, parse, visit, apply and summary steps; `Rename.gate` reimplements `Gate.applyOrRefuse` and says "would introduce" where every other edit says "would add" (refusal wording is contract) | Done | Full code review 2026-11-03. `Rename.gate` calls `Gate.applyOrRefuse`; the refusal says "would add", and its golden changed in the same PR. Record: `docs/sessions/rename-on-gate.md`. `MoveType` now says "would add" through the same gate (`docs/sessions/movetype-gate.md`) |
| 34 | 41 | `Extraction.apply` is about 160 lines (classify, decide return, build signature, compose text, compute lines); `Selection.body` Javadoc says "innermost" but takes the outermost body | Done | Full code review 2026-11-03: reading, not run. Split into `Plan` (refusals and classification), `callOf` (the call) and `methodText` (the render); `apply` is about 50 lines. The Javadoc now says outermost. Record: `docs/sessions/extraction-apply-split.md` |
| 35 | 42 | Server reports a non-refusal exception as "engine error" with no stack trace and nothing on stderr; `Tools.call` calls `Journal.abort()`, which may throw and hide the original error | Done | Full code review 2026-11-03. Fixed: the engine-error text has three frames and stderr has the trace; a failed undo is attached as suppressed (`Tools.abort`). Record: `docs/sessions/server-engine-error.md` |
| 36 | 43 | Names: `Stubs.label` duplicates `Rename.typeLabel`; `Stubs.typeName` and `Extract.typeName` disagree on type variables and captures; `Signatures.label` drops the outer class that `typeLabel` keeps | Done (2026-11-04) | Full code review 2026-11-03. `Names.typeLabel` replaces `Rename.typeLabel` and `Stubs.label`; `Signatures.label` keeps the outer class (golden `sig_add_refused_nested_class`). Type variables decided 2026-11-04: a stub writes a type variable by name when the type it goes in declares it, and erases it otherwise (`docs/sessions/stubs-type-variables.md`). Open: wildcards, which `Stubs` erases and `Extract` keeps. Record: `docs/sessions/names-one-class.md` Wildcards (decided 2026-11-04): a stub keeps `? extends T` as `Extract` does; captures are erased in both (`docs/sessions/stubs-keep-wildcards.md`). Golden: `stub_wildcard_argument`. | Closed 2026-11-04: `Stubs.label` and `Rename.typeLabel` are gone, `Signatures.label` uses `Names.typeLabel`, and wildcards were decided (`docs/sessions/names-close-43.md`).
| 37 | 44 | A column of 0 or below is accepted silently and falls back to name lookup, so `Positions.offset`'s refusal is never reached | Done | Full code review 2026-11-03: reading, not run. Small: refuse a given column below 1 |
| 38 | 45 | `SessionReport` hardcodes three tool sets that copy `Tools.ALL`, so a new tool counts as "other"; it matches the text "refused:" that `Server` writes | Done | `Tools.Def` has a `Kind` (read, refactoring, edit, control) and the report counts by it; `Refusal.PREFIX` is the one refusal prefix (a flag logged by `SessionLog` would still match text, since the hook sees only the result). Also: quoted text and heredoc bodies are not redirects, and a shell write counts lines of Java only for a `.java` target (friction log, 2026-11-03) |
| 39 | 46 | Layering: domain classes import `Tools.lines` and call `Edits.lineOf`, so they depend on the MCP registry; line helpers are spread over six classes | Done (2026-11-04): the line helpers were gone after #27; the result type is `Outcome` (its own file). `Edits.gated`, `parses` and `Written` stay in `Edits`, by choice (`docs/sessions/layering-line-helpers.md`) | Full code review 2026-11-03: reading, not run. Overlaps #27 (the line helper). Move the helpers to their own class first |
| 40 | 47 | `Gate.restore` and `Journal.restore` both write or delete files, with different parent-directory handling; `Gate.put` is a third writer | Done | Full code review 2026-11-03. `Journal.put` and `Journal.restore` are the one writer; `Gate` calls them. Record: `docs/sessions/file-writers-one-place.md` |
| 41 | 48 | `Gate.deferred` resets the flag to false in `finally`, not to its previous value, so a nested deferred call resets the outer one | Done | Full code review 2026-11-03. Fixed: a nested deferred call restores the outer flag (`GateTest.aNestedDeferredCallLeavesTheOuterOneDeferred`). Record: `docs/sessions/gate-deferred-nesting.md` |
| 42 | 49 | Misleading names: `Extract` maps each variable to "declared inside", so `used.get(r)` reads as "used"; `Stubs` `implicit` is set as `!created.contains(...)`, the inverse of its name | Done | Full code review 2026-11-03: reading, not run. Done for `Extraction`: `Plan.declaredInside` is the set of variables declared inside, so `callOf` asks `declaredInside().contains(r)`. Open: `Stubs` `implicit` matches its use; why a class this call created gets no default-constructor stub is not in the code (a question). Record: `docs/sessions/misleading-names.md` Stubs' `implicit` is renamed `needsDefault` (`docs/sessions/stubs-implicit-name.md`) |
| 43 | 50 | Process-global state cannot be isolated by tests: `Server` (`ONE_AT_A_TIME`, `CALLS`, `lastOutput`), `Imports.LIBRARIES` (never cleared), `Journal.DONE` (undo across projects) | Done | Full code review 2026-11-03: reading, not run. Overlaps #33 (static state); a `Session` object would hold these. Decide with #33 |
| 44 | 51 | Performance: `Signatures.Family.of` parsed every file per call; it now parses only the files that contain the method's name (`add_parameter` 6.5 s to 2.9 s, Commons Math, one run each). `Imports.toImport` and the stub rounds measured 2026-11-03: an import adds about 0.7 s to a 0.27 s call, a stub round 0.2 s and one more parse. Undo measured 2026-11-04 at under 1 ms: the 2.7-2.9 s first recorded was the harness's own uncached check, now timed apart (`docs/sessions/measure-undo.md`) | Done | Full code review 2026-11-03. Records: `docs/sessions/reuse-gate-parse.md`, `docs/sessions/measure-imports-stubs.md` (its undo figures are wrong; see `measure-undo.md`). Reusing the gate's parse across calls was not taken: it keeps every AST between calls |
| 45 | 52 | `move_type`: a static nested type to its own file, references, qualifications and private members following (the user chose a new tool over an option on `rename` or `replace_declaration`, and widening over refusing, 2026-11-03) | Done | Friction log 2026-11-03: `Extraction` (660 lines) split out by script. Record in `docs/sessions/move-type.md`; a trial move of `Signatures.Family` passed the gate and the tests |
| 46 | 53 | Command line: `bin/jrefactor`, one tool call per run, the undo stack kept between runs (gorefactor's `cmd/refactor`) | Done | The user asked (2026-11-03): another session ran in a container where the MCP server cannot run, and made every edit by hand. Record in `docs/sessions/cli.md`. Runs made faster (a read 9.4 s to 0.3 s, an edit 4.7 s to 2.4 s) in `docs/sessions/cli-cache.md` |
| 47 | 54 | Imports fixed only for a file under a source root: `create_file` of a file outside the source roots (a golden's expected file) was refused for an ambiguous name it does not need to resolve | Done | Friction log 2026-11-03 (the `move_type` golden). `Edits.gated` applies the text as written when `SourceProject.packageOf` is null. Record: `docs/sessions/imports-source-root.md` |
| 48 | 55 | A stub for a call that fits no overload of a project method: `add_declaration` of a test calling `add("apple", quantity)` on a `Cart` with only `add(String)` was refused, where a new name would have got a stub | Done | Friction log 2026-11-03 (`docs/sessions/cli-cache.md`). `Stubs.find` handles `IProblem.ParameterMismatch` as an undefined method. Record: `docs/sessions/stub-overload.md` |
| 49 | 56 | The check after an edit parses only the changed files and those that name a declaration whose header changed (`Headers`) | Done | Follow-up of #10 and the command line. Commons Math: one edit 2.1 s to 64 ms, `rename` 2.4 s to 1.3 s, `add_parameter` 3.2 s to 1.9 s, after the stress test's fixes. Records in `docs/sessions/narrow-check.md` and `docs/sessions/stress-narrow-check.md` |
| 50 | 57 | Signature changes change the lambdas that implement the method (add a parameter name, drop an unused one, reorder), instead of refusing them | Done, local session | Follow-up of `docs/sessions/refuse-lambda-implemented.md`. `MethodFamily`, `SignatureChange`; record in `docs/sessions/lambdas-follow-signature.md` |
| 51 | 58 | `move_type` drops an `import a.Outer` that a file in another package no longer uses once it imports `a.Inner` | Done, local session | Follow-up of `docs/sessions/move-type.md`. `MoveType`: such a file's imports go through `Imports.fix` with the moved type chosen. Record in `docs/sessions/move-type-unused-import.md` |
| 52 | 59 | One walk over "the files that hold the name" for `Rename` (field, method, type) and `MoveType` | Done, local session | Follow-ups of `docs/sessions/move-type.md` and `docs/sessions/rename-across-files.md`; with order 33's folding of `Rename`'s walks. `Rename`, `MoveType`; `Holding`, record in `docs/sessions/one-holding-walk.md` |
| 53 | 60 | `RefactoringStressTest` weights its targets toward fields, methods and types (local renames dominate now) | Done, local session | Follow-up of `docs/sessions/stress-refactorings.md`; record in `docs/sessions/stress-weighted-targets.md` |
| 54 | 61 | The stress tests restore an edited file after a killed run: write the original to a side file first, and restore it at the next start | Done, local session | Follow-up of `docs/sessions/stress-narrow-check.md`: a stopped run left a Commons Math file changed. `NarrowCheckStressTest`, `RefactoringStressTest`; `StressSnapshot`, record in `docs/sessions/stress-restore.md` |
| 55 | 62 | A batch checked at the end declares stubs only for its edit steps' code, not for a refactoring step's | Closed 2026-11-04: not reproducible (the attribution has no refactoring that leaves a name for a later edit) | Follow-up of `docs/sessions/no-stubs-for-refactorings.md`. `Batch`, `Gate`, `Stubs` Record: `docs/sessions/batch-stubs-by-step.md` |
| 56 | 63 | The server uses the command line's classpath cache at start (about 3 s each start) | Done | Follow-up of `docs/sessions/cli-cache.md`. `Server`, `SourceProject` Done in #80: `Cli.classpathCache`, `Server.load` (`docs/sessions/server-classpath-cache.md`) |
| 57 | 64 | The classpath cache notices a change to a parent pom (it is keyed by `pom.xml` alone) | Done | Follow-up of `docs/sessions/cli-cache.md`. `MavenBuild.pomHash` hashes the child's `pom.xml` and each parent pom it names. Record: `docs/sessions/classpath-cache-parent-pom.md` |
| 58 | 65 | `move_type` imports the moved type into a file of another package that names it unqualified through inheritance (`OutlineExtractor` implements `BSPTreeVisitor` and writes `Order`) | Done, local session | Found by `RefactoringStressTest` on Commons Math, seed 7 (`docs/sessions/stress-weighted-targets.md`). `MoveType`; also `public` for an interface\'s member type; record in `docs/sessions/move-type-inherited-users.md` |
| 59 | 66 | `extract_method` refuses statements that assign a blank final field (in a constructor or initializer) | Done | Found by `RefactoringStressTest` on Commons Math, seed 7: `Interval`'s `upper` was refused by the gate ("may not have been initialized"). `Extraction`; not on either session's list; record in `docs/sessions/extract-blank-final.md` |
| 60 | 67 | `inline_function` refuses calls whose inlined body would compile and behave differently: an argument with effects for a parameter the body does not use (dropped), two arguments with effects that the body reads in another order (reordered), an argument the parameter's type converts (`half(3)` with `double x` gives `3 / 2`), and a name in the body that means another declaration at the call (`FACTOR` of the caller's class) | Done; the gaps (an effect before an argument's use, a use in a lambda or branch) added by the local session, `docs/sessions/inline-function-effect-gaps.md` | Found by probes after `docs/sessions/stress-new-tools.md`; each applied and type-checked. `InlineFunction`. A check that every name in moved text resolves to the same binding after the edit would cover the last case for every tool that moves code |
| 61 | 68 | `extract_variable` and `inline_variable` refuse what moves a read across an effect or a name across a declaration: extracting `i * 2` from `i++ + (i * 2)` (read moved before `i++`), a new name that hides a field the rest of the method reads, an inlined initializer whose field a closer local hides at the use | Done | Found by probes after `docs/sessions/stress-new-tools.md`; each applied and type-checked. `ExtractVariable`, `InlineVariable` |
| 62 | 69 | `pull_up` refuses a method that, in the supertype, overrides one the supertype inherits: every other subclass then runs the pulled body (`Other.name()` returned "root", then "sub") | Done (2026-11-04), local session; record in `docs/sessions/pull-up-inherited-override.md` | Found by a probe after `docs/sessions/stress-new-tools.md`; applied and type-checked. `PullUp`, `MemberMove` |
| 63 | 70 | `pull_up` into a class whose body is `{}` on one line writes a syntax error, and the refusal blames the member ("Move only a member that uses nothing the subclass has") | Done (2026-11-04), local session; `push_down` had it too; record in `docs/sessions/member-move-one-line-body.md` | Found by a probe after `docs/sessions/stress-new-tools.md`. `MemberMove`; `push_down` into such a subclass was not tried |
| 64 | 71 | One shape for a refactoring: a shared target resolver (file, line, name, column to a type, member, statement or expression, with one refusal wording) and preconditions as values a tool lists, not `if`/`throw` in each `run`, so a check can refuse or report | Done for twelve tools (2026-11-04), local session: `Opened`, `Qualified`, `Precondition`; `Rename`, `MoveType`, `Extract`, `Declarations` and the signature tools keep their own resolvers (follow-up); record in `docs/sessions/refactoring-shape.md` | Review of the abstractions, 2026-11-04 (the user agreed to put it ahead of #73): 14 tool classes repeat the parse-and-locate preamble (13 copy `project.javaFile(rel)` then `Jdt.parse`), resolve targets their own way (`MemberMove.typeNamed`, `ExtractVariable.find`, `InlineVariable.variableAt`, `Rename.Query`), and refuse inline (22 refusals each in `MoveMember` and `InlineFunction`). A cross-cutting flag has no one place to attach. No behavior change; the goldens are the check |
| 65 | 72 | One effects analysis: what an expression or statement reads, writes and calls, in order, shared by the tools that move code; it replaces `ExtractVariable.hasEffect`, `MoveStatement.Effects`, `InlineVariable.assigns`/`readsIn` and `InlineFunction`'s argument checks, and has room for what may throw | Done (2026-11-04), local session: `Effects`; record in `docs/sessions/one-effects-analysis.md` | Same review: four partial models of effects, none knowing exceptions. #73 needs a fifth unless they become one. No behavior change; the goldens and the effect-order probes are the check |
| 66 | 73 | `preserve_exceptions` on every refactoring, and a separate section in the result naming each change to exceptional behavior (a dereference moved past a guard, a call declaring a checked exception moved, an expression moved into or out of a `try`) | Done (2026-11-04), local session: the flag, the result's own part, and three tools' first cases; record in `docs/sessions/preserve-exceptions.md` | The user's decision, 2026-11-04 (Decisions, and open question 0: one flag, a separate field). First case: `move_member`'s null receiver |

### Open, in order

Ordered by gorefactor's evidence (see "Why this order" below) and this repo's
friction log; reorder when this repo's own log says otherwise.

| Order | # | Item | Status | Evidence |
|---|---|------|--------|----------|
| 89 | 96 | A behavior gate beside the type-check gate: a tool declares what its edit does to code (moved from here to there, duplicated, dropped), and one checker derives the effect-order (`Effects`), same-meaning (`NameCheck`) and exceptional-behavior (`Exceptional`) checks from that, for every refactoring | Done (2026-11-05), local session: the user chose declared moves (Decisions); `Behavior` (a `Move`: code, root, landings) derives the effect-order, place, copy, exceptional and same-meaning checks; `inline_variable` is its first user, which fixed a field read moved past a call that changes it (`docs/sessions/behavior-gate.md`); `extract_variable` second, which did #99 and found two gaps in the first version (a read crossed by a moved effect; a write later in a loop) and applies the policy's guard and try rules as refusals (`docs/sessions/gate-extract-variable.md`); `move_statement` third, which refuses a call moved past a read it could change and a statement that may throw lifted above a guard (`docs/sessions/gate-move-statement.md`); `inline_function` fourth, each argument a move into the body (`docs/sessions/gate-inline-function.md`). Done: the other tools in the evidence move whole statements, members or bodies to where they run at the same point in the flow, so what they need is `NameCheck`, which they run, and their one-off rules (`move_member`'s null receiver, `convert_lambda`'s receiver timing) | Review of the abstractions, 2026-11-04 (the user agreed): the three checks exist but each tool opts in, and most do not. `Effects` is used by 6 tools, `NameCheck` by 3, `Exceptional` by 5; `extract_method`, `move_member`, `pull_up`, `push_down`, `function_to_method` and `convert_lambda` move code without the effect check, and `preserve_exceptions` is accepted but does nothing in about 30 refactorings. Subsumes the per-tool parts of #75-#77. `InlineFunction` (1,038 lines, 20 refusals) should shrink as its rules move into the gate |
| 90 | 97 | One schema vocabulary for `Tools`: `file`, `line`, `name`, `column` and the other shared arguments defined once and named by each `Def`, instead of hand-written JSON per tool | Done (2026-11-05), local session: `Arg` (`FILE`, `IMPORTS`, `string`, `integer`, `bool`, `oneOf`, `strings`, `json`, `required`, `described`); each `Def` lists its `Arg`s, and its schema, `required` and argument names are built from them. Every schema is the same as before (`ToolSchemasTest`, opt-in, diffed before and after); `Tools.java` 1,405 to 1,249 lines; record in `docs/sessions/schema-vocabulary.md` | Same review: `Tools.java` is 1,391 lines for 36 tools, with 114 hand-written string fields. Fits with splitting `Tools` (#32). No behavior change; `ToolsTest` and the goldens are the check |
| 91 | 98 | Shared helpers instead of copies: `lineOf`/`lineStart` (copied in `ExtractVariable`, `InlineVariable`, `Primitives` though `Lines` has them), `indentOf` (three copies), `within` (two), and a query helper for the common "find nodes like this" walks (53 anonymous `ASTVisitor`s) | Done (2026-11-05), local session: `Nodes.end`, `Nodes.within`, `Lines.offsetOf`, `Lines.indentOf` replace twelve copies (80 calls folded by `inline_function`); `Nodes.all(root, type)` is the query, used by seven walks that only collected; 22 more single-type visitors convert when their classes next change; record in `docs/sessions/shared-helpers.md` | Same review. No behavior change; the goldens are the check. Needed two tool fixes first: #169 (`inline_function`, a subtype passed on to a call) and #170 (`NameCheck`, keys that hold positions) |
| 13 | 11 | A/B harness run on a small Java task set against a stock baseline | Done (2026-11-05), local session: gorefactor's `cmd/refactor-ab` on a Java port of its warehouse traps (`eval-tasks/warehouse.json`), Sonnet, 4 runs per arm and task: every run passed in both arms (one manual run failed to start), mcp $0.12 a run against manual $0.09 (+32%; gorefactor's same set, +20%); record in `docs/sessions/ab-warehouse.md` | Needs edits, batch and stubs to be a fair test; gorefactor's fresh-model arm cost about twice the manual arm until they existed. Rerun after #99-#101 (2026-11-05, `docs/sessions/ab-warehouse-2.md`): 32/32 passed, mcp $0.119 against manual $0.096 a run (+24%, from +31%); reorder now cheaper with the tools; the rest is #102 and #103. Third run after them (`docs/sessions/ab-warehouse-3.md`): mcp $0.095 against manual $0.093 a run (+2%); inline and reorder cheaper with the tools; one dedupe run folded only the shared statement and failed the task's structure test |
| 92 | 99 | `extract_variable` names an expression with effects when nothing evaluated before it in its statement reads what it could change: the first `p.bin()` of `transfer(p.bin(), p.bin(), p.qty())`, `reserve(truck)` in `both(reserve(truck), charge(truck))` | Done (2026-11-05), local session: by moving `extract_variable` onto the behavior gate (#96; `extract_variable_first_effect`) | A/B run (`docs/sessions/ab-warehouse.md`): refused in every mcp run of `warehouse-reorder` and in two of `warehouse-inline`, each then rewritten with `replace_statements`; the refusal had told it to do exactly that extraction. `ExtractVariable.checkPlace` refuses any expression with effects |
| 93 | 100 | `batch` accepts a step's tool by its client name (`mcp__jrefactor__rename`, any server prefix) | Done (2026-11-05), local session: `Tools.named` strips `mcp__<server>__`, so the CLI takes it too; golden `batch_client_names` | A/B run: 6 batches in 4 of 16 mcp runs were refused as "No tool mcp__gorefactor__..." and resent |
| 94 | 101 | `extract_variable`'s column refusal names the columns where the expression starts on the line | Done (2026-11-05), local session: `extract_variable` (a wrong column, and two occurrences) and `inline_variable` (a wrong column, which said "no variable named", and two variables) name the columns, by `Nodes.columns`; `rename` and `convert_lambda` already did. Goldens `extract_variable_refused_wrong_column`, `inline_variable_refused_wrong_column` | A/B run: three wrong columns guessed in two runs (`p.bin()` at 14 and 18, it was 16), a turn each |
| 95 | 102 | `inline_function` binds an argument to a local at the call when inlining it in place would change behavior (an effect run in another order, more than once, or only sometimes), as a standard inline does, instead of refusing | Done (2026-11-05), local session: `InlineFunction.bindings`; nine refused goldens now apply (`inline_function_binds_*`), the warehouse trap inlines in one call each (`inline_function_binds_short_circuit`; the A/B's hidden test passes on its output); `inline_function_refused_bind_crosses` when the binding would cross an effect; record in `docs/sessions/inline-binds-arguments.md` | A/B rerun (`docs/sessions/ab-warehouse-2.md`): `warehouse-inline` cost the mcp arm 17.8 turns against 10.5. Each of `both`, `either` and `within` was refused (correctly), "put one of them in a variable first" left the order to the model, and extracting `charge(truck)` before `reserve(truck)` was refused as a reorder; 20 refusals in 4 runs |
| 96 | 103 | Fold: replace code that does what an existing method does with a call of it (gorefactor's `fold`), so near-duplicates merge by extract, then fold | Done (2026-11-05), local session: `fold` (`Fold`), static methods, expression and statement bodies, the `String.format` text relaxation; the warehouse dedupe is `add_declaration` of the helper and one `fold` (9 of 10 folded, `Gas`'s `>` left; the A/B's hidden test passes); record in `docs/sessions/fold.md` | A/B rerun: `warehouse-dedupe` cost the mcp arm $0.15 against $0.09, with no refusals: reading (14 `inspect`s in 4 runs) and composing each of ten replacements by hand. gorefactor's derivation for this task is extract_variable, extract_function, fold |
| 14 | 16 | Opt-in stress tests on a real project for tools whose rules are heuristics, with a counts-only mode | Partly done; behavior probes run with every build since 2026-11-05 (`ProbeTest`, 22 cases: a program run before and after a tool; `docs/sessions/probe-test.md`) | From gorefactor; compare versions on identical input; `NarrowCheckStressTest` for the narrowed type check (`-Djrefactor.stress.root`, `.edits`, `.seed`, `.counts=true` for counts only), record in `docs/sessions/stress-narrow-check.md`; `RefactoringStressTest` calls the refactorings on random targets (applies or refuses, adds no error by a full count, undo restores every file): no failures in 160 calls on Commons Lang and Math, three refusals by the gate that are tools' missing preconditions (`docs/sessions/stress-refactorings.md`); the five newer refactorings added 2026-11-04, no failures in 220 calls, behavior changes found by probes instead (#67-#70, `docs/sessions/stress-new-tools.md`) |
| 16 | 15 | Read guard: a hook that sends reads of `.java` files through `inspect` | Done (2026-11-05, the user asked for it with the write guard): `bin/read-guard`, `Guard` (`read`); Read of a `.java` file and sed, cat, head, tail, less, more, nl, awk or grep naming one are denied, files under `resources/` allowed; a `grep` that printed no text (`-c`, `-l`, `-q`) was allowed for part of 2026-11-05 (#201), then refused again once `inspect` had `pattern` (#202) | In gorefactor's A/B the guarded arm did worse ($2.95, 5/8 against $2.71, 6/8); its case is this repo's own sessions, where gorefactor's ended reading by shell (its cost-history 22) |
| 97 | 104 | Write guard, an experiment: a hook that sends changes to `.java` files through the tools | Done (2026-11-05, the user: "experiment with a write guard"), as gorefactor's `cmd/write-guard`: `bin/write-guard`, `Guard` (`write`); Edit, Write and MultiEdit of a `.java` file, and sed -i, perl -i, tee, cp, mv, rm or a redirection into one, are denied; `./mvnw spotless:apply`, git and `resources/` allowed | What to learn: how many writes it refuses and whether each then went through a tool (`bin/session-report` counts denied calls), and its cost in turns; gorefactor's roadmap 53 |
| 18 | 12 | Trim the transitive JDT jar set | Open, low priority | Caveat in question 1 |
| 25 | 32 | Split large classes when each is next changed: `Extract` (1,048 lines), `Signatures` (753), `Declarations` (597, 26 static methods), `Tools` (schemas and dispatch) | Partly done, cloud session | Code review 2026-11-03. No behavior change; no split without a change that needs it. `Signatures` is split (`MethodFamily`, `SignatureChange`; the other session, 2026-11-03). `Extraction` (the nested class of `Extract`, about 660 lines) is its own file (`docs/sessions/split-extract.md`); `Signatures`, `Declarations` and `Tools` are not split; `Signatures` (818 lines) split by `move_type` into `Signatures` (365, the entry points and shared helpers), `MethodFamily` (327) and `SignatureChange` (153), record in `docs/sessions/split-signatures.md` |
| 67 | 74 | Move the remaining tools onto the refactoring shape: `Rename` (`Query`), `MoveType`, `Extract`, `Declarations` (`find`) and the signature tools (`MethodFamily.of`) resolve targets through `Opened`; the up-front checks still written as `if`/`throw` in `InlineVariable`, `ExtractVariable`, `ChangeType` and `EncapsulateField` become `Precondition`s | Closed (2026-11-04, the user's choice): `Declarations` parses for syntax only on purpose (a binding parse would slow every edit), `Extract` checks the range against the text before it parses, and `MoveType`, `Rename` and the signature tools read many files (`Holding`, `parseAll`) where `Opened` is one; the twelve tools on `Opened` stay | Follow-ups of #71 (`docs/sessions/refactoring-shape.md`). No behavior change; the goldens are the check |
| 68 | 75 | `preserve_exceptions` detects more: a dereference moved past a guard, code moved into or out of a `try`, an `inline_function` argument that may throw and now runs later in the body, an `extract_variable` expression that may throw moved before an earlier state change in its statement | Done (2026-11-05), local session: `inline_function` arguments used only sometimes, `inline_variable` initializers moved into a guard, loop, lambda or `try` (`docs/sessions/exceptional-more.md`); `move_statement` past a guard (a statement with a side effect is refused, one that may throw reported) and `extract_variable` out of a try's resources (`docs/sessions/move-statement-guard.md`). An argument that may throw and now runs after a change in the body, and an extracted expression moved before an earlier change, were already refused by the normal-flow rules (they read state) | Follow-ups of #73 (`docs/sessions/preserve-exceptions.md`); the user's policy asks for the syntactically clear cases (Decisions, 2026-11-04) |
| 69 | 76 | Mark code that runs only sometimes on `Effect` (`conditional`), replacing the parent walks in `ExtractVariable.checkPlace` (loop conditions, `&&` and `\|\|`, conditionals) and `InlineFunction.deferral` | Done (2026-11-05), local session: `Effects.later` and `Effects.sometimes` (a query on `Effects`, not a field on `Effect`), used by both; they add the branches of statements (`if`, loops, `switch`, `catch`), which fixed `inline_function` moving an argument with an effect into an `if` or a loop (found by a probe, applied and type-checked); record in `docs/sessions/effect-conditional.md` | Follow-up of #72 (`docs/sessions/one-effects-analysis.md`). Not a pure refactoring after all: it fixed two behavior changes and allows the condition of a `?:` and the left of `&&` in `extract_variable` |
| 70 | 77 | A check that every name in moved text resolves to the same declaration after the edit, for every tool that moves code; it would replace one-off rules such as `inline_function`'s other-class member refusal and `inline_variable`'s shadow refusal | Done (2026-11-04), local session: `NameCheck` in `inline_function`, `inline_variable` and `convert_lambda`; `pull_up`, `push_down` and `move_member` too (second PR); `extract_method` and `move_type` too (`docs/sessions/same-meaning-extract-move.md`); the one-off rules stay for their clearer refusals; record in `docs/sessions/same-meaning-check.md` | Follow-up of #67 and #68 (`docs/sessions/effect-order.md`); the general form named in row 60's evidence |
| 71 | 78 | `inline_function` accepts an argument whose type is a subtype of the parameter's (a `String` for an `Object`), refused today by the conversion rule; numeric and boxing conversions stay refused | Done (2026-11-04), local session: a subtype when every use is a call receiver; record in `docs/sessions/inline-function-subtype.md` | Follow-up of `docs/sessions/effect-order.md`: the rule is broader than the probe (`half(3)` for `double`) needed. Needs a golden showing the body behaves the same |
| 72 | 79 | `bin/jrefactor --rebuild` (or `JREFACTOR_REBUILD=1`) builds the jar from the working tree first, so a session can probe uncommitted server code | Done (2026-11-04), local session: `--rebuild` and `JREFACTOR_REBUILD=1` in `bin/jrefactor` | Friction log, 2026-11-04: probing #73's change took a temporary commit and a soft reset |
| 73 | 80 | `move_type` moves a top-level type to another package (an option, `package`): its file moves, its package line and every reference and import follow, and members used across the new package boundary are widened or refused | Done (2026-11-04), local session: `move_type` with `package`, `MoveToPackage`; record in `docs/sessions/move-type-package.md` | Friction log: "No tool moves a type or…", golden and fixture moves by hand. The user's order of 2026-11-04 (recommendation 1). An option on `move_type`, not a tool, unless its design note finds otherwise |
| 74 | 81 | `move_file`: move or copy a file that is not Java (a fixture, a doc, a golden case directory), as gorefactor's; and delete a directory | Done (2026-11-04), local session: `move_file` (`copy`, `delete`); record in `docs/sessions/move-file.md` | Friction log: `move_file` idea; "no tool moves golden directories"; `rm` of directories by hand |
| 75 | 82 | Change a method's parameter or return type, every override and call following (gorefactor's `change_parameter_type`; Java adds the return type) | Done (2026-11-04), local session: `change_type` with `parameter` or `return_type`; record in `docs/sessions/change-signature-type.md` | Recommendation 2 (2026-11-04): #125 changed `appendTo`'s return type with an `add` to `addAll` edit at each of four callers. Extends the signature tools; `change_type` does fields only |
| 76 | 83 | Safe delete: delete a member or type that nothing uses, refused with the uses when something does (gorefactor's `remove_dead_code`) | Done (2026-11-04), local session: `replace_declaration`'s delete, `SafeDelete`; record in `docs/sessions/safe-delete.md` | Recommendation 3 (2026-11-04). Perhaps an option on `replace_declaration`, which deletes with empty content and refuses a used declaration through the gate |
| 77 | 84 | Widen existing tools: `inline_function` for instance methods and bodies of several statements; `extract_method` with several results; `rename` of type parameters | Done (2026-11-04), local session: `rename` of type parameters done (`docs/sessions/rename-type-parameter.md`); `inline_function` bodies of several statements and instance methods done (`docs/sessions/inline-function-statements.md`, `inline-function-instance.md`); `extract_method` with several results in a record done (`docs/sessions/extract-method-record.md`) | The user's list of 2026-11-04. `extract_method`'s refusal of two results was the user's choice of 2026-11-03 (over returning a record): widening it needs the user's choice again. `rename` refuses type parameters (stress runs) |
| 78 | 85 | Extract interface and extract superclass: a new type from members of a class, the class implementing or extending it | Done (2026-11-04), local session: `extract_supertype` (`kind` interface or class); record in `docs/sessions/extract-supertype.md` | The user's list of 2026-11-04; `pull_up` needs the supertype to exist |
| 79 | 86 | Introduce constant, field and parameter: `extract_variable`'s expression as a `static final` field, an instance field, or a new parameter of the enclosing method (callers pass the expression) | Done (2026-11-04), local session: `extract_variable` with `as`; record in `docs/sessions/introduce-constant-field-parameter.md` | The user's list of 2026-11-04. Perhaps options on `extract_variable`; the parameter form composes with `add_parameter` |
| 80 | 87 | Lambda, anonymous class and method reference, each to the others where the meaning is kept | Done (2026-11-04), local session: `convert_lambda`; record in `docs/sessions/convert-lambda.md` | The user's list of 2026-11-04; `inline_function` and `move_member` refuse method references and ask for a lambda first |
| 81 | 88 | Convert a class to a record (final fields set once by the constructor, accessors renamed or kept) | Done (2026-11-04), local session: `convert_to_record` (`value_semantics`); record in `docs/sessions/convert-to-record.md` | The user's list of 2026-11-04 |
| 82 | 89 | Extract class and introduce parameter object (gorefactor's `extract_struct`): fields or parameters grouped into a new type, uses following | Done (2026-11-04), local session: introduce parameter object (`extract_struct` with `parameters`) and extract class (with `fields`); record in `docs/sessions/extract-struct.md` | The user's list of 2026-11-04; on the planned list in `docs/tool-contract.md` |
| 83 | 90 | Instance method to static and back in place (gorefactor's `method_to_function`, `function_to_method`) | Done (2026-11-04), local session: `move_member` to its own class, and `function_to_method`; record in `docs/sessions/static-in-place.md` | The user's list of 2026-11-04; `move_member` does it only while moving to another class |
| 84 | 91 | Edit primitives: `replace_expression`, `insert_statements`, `replace_statements`, `add_case`, `add_element` (gorefactor's names) | Done (2026-11-04), local session: the five primitives, `Primitives`; record in `docs/sessions/edit-primitives.md` | The user's list of 2026-11-04; option A of the ten-tools decision (`docs/how-this-project-was-built.md`) |
| 85 | 92 | `function_to_method` refuses when a subclass (or superclass) already has an instance method of the new signature: it would start to override (or be overridden by) it, and `x.area(3)` on a `Square` would run `Square.area(int)` | Done (2026-11-04), local session: `FunctionToMethod.refuseOverriding`; golden `function_to_method_refused_override` | Probe after `docs/sessions/stress-newer-tools.md`: applied and type-checked; 3 became 9. `FunctionToMethod` |
| 86 | 93 | `inline_function` qualifies a bare member of the body (`count`) that a local or parameter of the calling member hides (`this.count`, or the type for a static) | Done (2026-11-04), local session: `InlineFunction.shadowEdits`; golden `inline_function_shadowed_field` | Probe after `docs/sessions/stress-newer-tools.md`: `total()` returning `count * 2` inlined into a method with a local `count` read the local. `InlineFunction` |
| 87 | 94 | `convert_lambda` to an anonymous class refuses a bare call of a method the interface inherits (a default of a superinterface), not only of those it declares | Done (2026-11-04), local session: `ConvertLambda.refuseSelf` walks the supertypes; golden `convert_lambda_to_anonymous_refused_inherited` | Probe after `docs/sessions/stress-newer-tools.md`: `() -> label()` became an anonymous class whose `label()` is the inherited default. `ConvertLambda.refuseSelf` |
| 88 | 95 | `extract_struct` refuses a grouped parameter whose type uses a type variable or a type the new file cannot see (a private nested type), naming it, instead of the gate's error | Done (2026-11-04), local session: `ExtractStruct.unusable`; golden `extract_struct_refused_type_variable`; the Commons Math case (`SchurTransformer.ShiftInfo`) now refused by name | `RefactoringStressTest` on Commons Math: three `extract_struct` calls gated ("creating PairZq would add compile errors"). `ExtractStruct` |
| 98 | 105 | Ambiguous imports: name every ambiguous simple name in one refusal; choose, without refusing, the candidate the file imported before the call or batch, or the one in a package the file's other imports come from; otherwise suggest `java.*` first, not the last alphabetically; say in the result which was chosen | Done (2026-11-05), local session: `Imports.chosen`, the refusal collected in `Imports.toImport`; goldens `imports_chosen_by_package`, `imports_chosen_prior_in_batch`, `imports_refused_every_name`; record in `docs/sessions/ambiguous-imports.md` | Friction triage (2026-11-05, `docs/sessions/friction-triage.md`): six entries, 2026-11-02 to 11-04 (the suggested candidate, one name per refusal, the `org.eclipse.jdt.core.dom` family, `Path` refused after its import was removed, JUnit's `MethodInvocation` suggested, a batch step removing an import a later step uses); `Imports.toImport` refuses at the first name and suggests the last |
| 99 | 106 | `replace_text` when `old` is not in the file: name the nearest lines, and accept an `old` that differs only in indentation when it then matches once, as gorefactor's does | Done 2026-11-06: a uniform indentation shift, `new` shifted with it (the user's choice over gorefactor's looser rule); the nearest lines named on a miss | Friction triage: two mismatches on 2026-11-02, both indentation copied from `inspect`; the session notes' traps record the same mistake since |
| 100 | 107 | Stubs: a static field read as `T.NAME` (the class, and the field typed from its use), and a stub replaced by a declaration whose parameters have the same erasure | Open | Friction triage: `SessionLog.MAX_RESULT` (2026-11-03), and `allPure(List)` clashing with `allPure(List<?>)` in a batch (2026-11-02); both reproduced on 2026-11-05 |
| 101 | 108 | A batch checked at the end fixes imports after its last step, so a name a later step declares (a member type) is not imported from a library | Open | Friction triage: `javax.management.Query` imported for a nested record a later step added (2026-11-03), reproduced on 2026-11-05 |
| 102 | 109 | `move_member` to a class in another file, and of static fields (constants included) as well as methods | Open | Friction triage: two moves into another file's class done by hand (2026-11-03, the end-of-batch check into `Gate`, the file writers into `Journal`); `move_member` moves only methods, within one file. Static fields added 2026-11-05 (the user): a constant cannot be moved to another class at all today, while Go and TypeScript move one with `move_declaration` |
| 103 | 110 | Refusals that say where: content that does not parse as members names its line, and a `create_file` refused for compile errors says that a batch with check "end" can fix them in later steps | Open | Friction triage: one blind retry (2026-11-02), one large file sent again (2026-11-03) |
| 104 | 111 | `inspect` with no arguments lists the project's source files, as gorefactor's module overview | Open, low priority | Friction triage: one probe guessed Maven paths (2026-11-02); `pattern` finds files by their content meanwhile |
| 105 | 112 | Go, TypeScript and Java at parity: the bugs, one name per operation across the servers, the tools and options Java lacks (add_result, parameterize, merge_duplicate_function, the type switch pair, split, inline class), and those Go and TypeScript lack from here. The survey and order are gorefactor's `docs/parity.md` (roadmap 54) | Planned (2026-11-05, the user) | Section 1 there lists this repo's bugs: replace_text's silent match inside indentation first, then #106-#108 |
| 106 | 113 | A class stub declared from a test file goes in the main source root, in the test's package: today it lands beside the test (`src/test/java`), and no tool moves Java between source roots | Open | Friction log 2026-11-05 (a probe of creating a file test first): `create_file` of a test using an undeclared `Cart` put `Cart.java` in `src/test/java`; `move_file` and `move_type` cannot move it to `src/main/java`; creating the empty class first works |
| 107 | 114 | `create_file` with `declaration`: a type from its declaration, empty, in the main source root (or the test root with `test`), with stubs for the abstract methods its supertypes leave unimplemented | Done (2026-11-05), local session: `Skeleton`, the path still the `file` argument (the root choice waits for #113); goldens `create_file_declaration_*`; record in `docs/sessions/create-from-declaration.md` | The first slice of constructive transformations (the user, 2026-11-05; "Constructive transformations" above) |
| 108 | 115 | A test from its target: `create_file` with `test_for` (the test class, mirrored package), `add_declaration` with `test_for` (a red test method calling the target) | Done (2026-11-05), local session: `TestFor`; the path is the `file` argument (no mirrored path yet); goldens `create_file_test_for_*`, `add_declaration_test_for` (with `GoldenTest`'s `classpath=junit`); record in `docs/sessions/test-from-target.md` | The first slice |
| 109 | 116 | Skeleton-only creation, an experiment flag: a Java `create_file` with member declarations is refused, naming the constructive operations | Done (2026-11-05), local session: `Session.skeletonOnly` from `JREFACTOR_SKELETON_ONLY=1`, checked in `Edits.createFile`; goldens `create_file_skeleton_only_*`; record in `docs/sessions/skeleton-only.md`; the A/B arm waits for the user's yes | The first slice; off by default, an A/B arm turns it on |
| 110 | 117 | An assertion method used in a test class that imports none (`assertEquals` in members added to a class made by `declaration`) is imported statically from the test framework, not declared as a stub | Done | A/B `shop-sonnet-skeleton` (2026-11-05): the stub `CurrencyExportTest.assertEquals(boolean, boolean)` was declared, then fought for six calls (`docs/sessions/ab-shop-tasks.md`) |
| 111 | 118 | `extract_struct` with `fields` on a record: components grouped into a new record, every construction and accessor use following | Done | A/B `shop-sonnet-skeleton` (2026-11-05): the address task's model tried it on `Order` first and was refused ("not a top-level type ... that resolves") |

### The 2026-11-04 list

The user asked (2026-11-04) for the missing refactorings in this order: the
three recommended from the friction log (#80 with #81, #82, #83), then the
widening of existing tools (#84), extract interface and superclass (#85),
introduce constant, field and parameter (#86), lambda, anonymous class and
method reference (#87), record conversion (#88), extract class and parameter
object (#89), instance to static and back (#90), and the edit primitives
(#91). One PR per item; each item's design note decides whether it is a tool
or an option on one, as CLAUDE.md prefers. Not on the list, left for the
friction log to raise: renaming a package, encapsulating a collection.

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

Edits are written to disk, the files whose errors can have changed are parsed
again, and the new compile errors are compared with the old ones by file and message (not line,
so moving code does not count). Any new error means the file is restored and the
edit refused. The gate parses every file in one batch (`Jdt.parseAll`, one JDT
environment for the whole set), and reuses the last count when every file's
text is what it saw, so an edit's count after is the next edit's count before.
On Commons Math (990 files, 224k lines) one edit cost about 2 s (roadmap #10).

The check after an edit is narrowed (roadmap #56): each file's `Headers` (for
each simple name it declares, a hash of the declarations' text up to their
bodies, with the package and the imports that text names) are kept with the
count, members keyed by their type (`Shop#total`), with the type names each
header mentions. Only the changed files are parsed again, plus: the files of
every type that is "changed" (a type whose header or member changed, a type
whose header names a changed type, as a subtype does, and the type of a member
whose header names one), repeated until nothing grows; and the files that
mention, as a whole identifier, a name any of those touched, or write `new T`
of a changed type (an anonymous subclass). A body change re-checks one file.
On Commons Math, one edit takes 64 ms, a field rename in 10 files 1.3 s (114
files re-checked) and `add_parameter` 1.9 s (124 files). `GoldenTest`
compares every step's count with a fresh one; `NarrowCheckStressTest` makes
random declaration edits to a real project and compares each (roadmap #16):
a first version, which followed names only, missed three of 60 on Commons
Math, all through inheritance, and the rule above missed none of 240 there
and 100 on Commons Lang.
