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

**Decided 2026-11-03: stubs travel in the call's result, not a side channel.**
An edit's declared stubs are returned in the result object of its call. Today
`Stubs` collects them in a `ThreadLocal` and `Tools.call` appends them to the
text, so a handler's output depends on state it does not return. The user chose
the result object. Not chosen: keep the side channel (the option it beats),
which needs no change to handlers but hides their output from tests and from
any caller that is not `Tools.call`.

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
open items is the order of the second table. Status as of 2026-11-03. When an
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
| 9 | Field, method and type rename across files, Javadoc included: one `rename` replacing `rename_local` (the user's choice of 2026-11-03 over a second tool or widening `rename_local`); a name with two meanings on the line is refused and picked by `Counter.count`, `local count` or column (the user's choice over "the local wins") | Record in `docs/sessions/rename-across-files.md` |
| 10 | Measure gate cost on a large project; cache the reparse | Commons Math (990 files): one edit 21.8 s to 2.1 s, `add_parameter` 15.0 s to 4.1 s, by one batch parse (`Jdt.parseAll`) and reusing the last count while no file changed. Opt-in `GateCostTest`; record in `docs/sessions/gate-cost.md` |
| 24 | Results that do not echo what the model sent; locations, not diffs | Audit found no result that repeats input or a diff; the largest result is the stub list (follow-up). Record in `docs/sessions/results-without-echo.md` |

### Open, in order

Ordered by gorefactor's evidence (see "Why this order" below) and this repo's
friction log; reorder when this repo's own log says otherwise.

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
| 13 | 11 | A/B harness run on a small Java task set against a stock baseline | Planned | Needs edits, batch and stubs to be a fair test; gorefactor's fresh-model arm cost about twice the manual arm until they existed |
| 14 | 16 | Opt-in stress tests on a real project for tools whose rules are heuristics, with a counts-only mode | Partly done | From gorefactor; compare versions on identical input; `NarrowCheckStressTest` for the narrowed type check (`-Djrefactor.stress.root`, `.edits`, `.seed`, `.counts=true` for counts only), record in `docs/sessions/stress-narrow-check.md`; `RefactoringStressTest` calls the refactorings on random targets (applies or refuses, adds no error by a full count, undo restores every file): no failures in 160 calls on Commons Lang and Math, three refusals by the gate that are tools' missing preconditions (`docs/sessions/stress-refactorings.md`) |
| 15 | 17 | A test that holds the tool count and the instructions' size, raised only with the user | Open | gorefactor's tool budget |
| 16 | 15 | Read guard: a hook that sends reads of `.java` files through `inspect` | Planned | Last: in gorefactor's A/B the guarded arm did worse ($2.95, 5/8 against $2.71, 6/8) |
| 17 | 25 | `pull_up` and `push_down` for methods (and fields): move a member to a supertype or to its subtypes, with the type-check gate behind it | Open | The user asked, 2026-11-03; no friction entry yet, so it waits for one. Also worth a look for gorefactor (Go has embedding, not inheritance: the nearest is moving a method between a struct and the one it embeds) and the TypeScript port |
| 18 | 12 | Trim the transitive JDT jar set | Open, low priority | Caveat in open question 1 |
| 19 | 26 | Path refusal in one place: `SourceProject` returns a `.java` path or refuses, instead of five checks with different wording (`Declarations`, `Extract`, `Inspect`, `Signatures`, `Edits`) | Done | Code review 2026-11-03. Refusal wording is contract: golden `refused.txt` fragments must hold |
| 20 | 27 | One line helper, `Lines`: `lineStart` and `lineEnd` (identical in `Declarations` and `Imports`), `Edits.lineOf`, and the "line N" / "lines N, M" / "lines A-B" text in `Tools`, `Rename` and `Extract` | Done | Code review 2026-11-03 |
| 21 | 28 | One ASTParser configuration: `Declarations.members` sets its own options and no environment; `Jdt` should own every parser | Done | Code review 2026-11-03. `Jdt` owns every parser now (`Jdt.parseMembers`, `options()`); record `docs/sessions/one-parser-config.md` |
| 22 | 29 | Name the two `ancestor` helpers apart: `Extract` starts at the parent, `Stubs` at the node itself | Done (2026-11-04): `Extract.ancestorAbove`, `Stubs.ancestorOrSelf`, `docs/sessions/ancestor-helper-names.md` | Code review 2026-11-03. Same name, different meaning |
| 23 | 30 | Stubs travel in the call's result object, not `Stubs.said()` and a `ThreadLocal` (decided 2026-11-03, see Decisions) | Done | `Tools.Outcome`, `Stubs.Declared`, `Stubs.Check`; record in `docs/sessions/stubs-in-result.md` |
| 24 | 31 | The before/after type check goes through `Gate`: `Batch` and `Stubs` each re-implement it | Done | Code review 2026-11-03. `Gate.checkedAtEnd` is the end-of-batch check; `Batch` calls it. `Stubs.declare` stays the declare loop. Record: `docs/sessions/gate-before-after.md` |
| 25 | 32 | Split large classes when each is next changed: `Extract` (1,048 lines), `Signatures` (753), `Declarations` (597, 26 static methods), `Tools` (schemas and dispatch) | Partly done, cloud session | Code review 2026-11-03. No behavior change; no split without a change that needs it. `Signatures` is split (`MethodFamily`, `SignatureChange`; the other session, 2026-11-03). `Extraction` (the nested class of `Extract`, about 660 lines) is its own file (`docs/sessions/split-extract.md`); `Signatures`, `Declarations` and `Tools` are not split; `Signatures` (818 lines) split by `move_type` into `Signatures` (365, the entry points and shared helpers), `MethodFamily` (327) and `SignatureChange` (153), record in `docs/sessions/split-signatures.md` |
| 26 | 33 | Static mutable state: `Journal.DONE`, `Imports.LIBRARIES`, `Server.CALLS`, `Stubs.DECLARED`; tests in one JVM can interfere | Done | Code review 2026-11-03. Safe in the server today: one call at a time |
| 27 | 34 | One `ObjectMapper` shared by `SessionLog` and `SessionReport` | Done | Code review 2026-11-03. `SessionReport` uses `SessionLog.JSON`. Record: `docs/sessions/shared-object-mapper.md` |
| 28 | 35 | `create_file` fixes imports before the gate, as every other edit does; a new file's unimported library type is now declared as a project class (`List.java`) | Done | Full code review 2026-11-03; reproduced by `create_file_java_imports`. `createFile` now goes through `write`, which fixes imports before the gate |
| 29 | 36 | Overload parameter lists split at commas inside generics (`Map<String, Integer>` breaks), so `replace_declaration`, `add_parameter`, `remove_parameter` and `move_parameter` refuse such overloads with names the model cannot type back | Done | Full code review 2026-11-03: reading, not run. `Declarations` splits on `,` |
| 30 | 37 | `Stubs.uses` gives every file the import picks of every stub, so an ambiguous name in one file can resolve to a class another stub named | Done | Full code review 2026-11-03: reading, not run. `Imports.fix` takes the picks |
| 31 | 38 | A failed Maven classpath is only on stderr: the server runs with no classpath and the model sees only "cannot be resolved" | Done | Full code review 2026-11-03: reading, not run. `SourceProject`, `Server` |
| 32 | 39 | Edit pipeline in one place: parse, edit text, `Imports.fix`, `Gate.applyOrRefuse` is written out at six sites (`Declarations` x3, `Edits`, `Extract`, `Signatures`), and text application four ways (`Rename.apply`, `Signatures.Change.apply`, `Edits.replaceText`, `Declarations.plan`) | Partly done | Full code review 2026-11-03. The import fix and the gate are one call (`Edits.gated`) at Declarations (3), Extraction and `Edits.write`; `Signatures` (per-file loop) and the four text-application ways are not done. Record: `docs/sessions/edit-pipeline.md` Text application: `TextEdit` (one record, one `apply`) is used by `Rename`, `MoveType` and `SignatureChange`; `Declarations.plan` and `Edits.replaceText` keep their own shape (see the record). Record: `docs/sessions/edit-pipeline-text.md` |
| 33 | 40 | Rename: `renameField`, `renameMethod` and `renameType` repeat the same read, parse, visit, apply and summary steps; `Rename.gate` reimplements `Gate.applyOrRefuse` and says "would introduce" where every other edit says "would add" (refusal wording is contract) | Done | Full code review 2026-11-03. `Rename.gate` calls `Gate.applyOrRefuse`; the refusal says "would add", and its golden changed in the same PR. Record: `docs/sessions/rename-on-gate.md`. `MoveType` now says "would add" through the same gate (`docs/sessions/movetype-gate.md`) |
| 34 | 41 | `Extraction.apply` is about 160 lines (classify, decide return, build signature, compose text, compute lines); `Selection.body` Javadoc says "innermost" but takes the outermost body | Done | Full code review 2026-11-03: reading, not run. Split into `Plan` (refusals and classification), `callOf` (the call) and `methodText` (the render); `apply` is about 50 lines. The Javadoc now says outermost. Record: `docs/sessions/extraction-apply-split.md` |
| 35 | 42 | Server reports a non-refusal exception as "engine error" with no stack trace and nothing on stderr; `Tools.call` calls `Journal.abort()`, which may throw and hide the original error | Done | Full code review 2026-11-03. Fixed: the engine-error text has three frames and stderr has the trace; a failed undo is attached as suppressed (`Tools.abort`). Record: `docs/sessions/server-engine-error.md` |
| 36 | 43 | Names: `Stubs.label` duplicates `Rename.typeLabel`; `Stubs.typeName` and `Extract.typeName` disagree on type variables and captures; `Signatures.label` drops the outer class that `typeLabel` keeps | Partly done | Full code review 2026-11-03. `Names.typeLabel` replaces `Rename.typeLabel` and `Stubs.label`; `Signatures.label` keeps the outer class (golden `sig_add_refused_nested_class`). Type variables decided 2026-11-04: a stub writes a type variable by name when the type it goes in declares it, and erases it otherwise (`docs/sessions/stubs-type-variables.md`). Open: wildcards, which `Stubs` erases and `Extract` keeps. Record: `docs/sessions/names-one-class.md` |
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
| 59 | 66 | `extract_method` refuses statements that assign a blank final field (in a constructor or initializer) | Open | Found by `RefactoringStressTest` on Commons Math, seed 7: `Interval`'s `upper` was refused by the gate ("may not have been initialized"). `Extraction`; not on either session's list |

### Split between sessions (2026-11-03)

The user asked for the next ten reasonable items, five of them taken by the
local session, chosen so its changes sit close together in the code, and the
other five left to the cloud session. Each row's status says which.

- **Local session** (the refactorings and their stress test: `MethodFamily`,
  `SignatureChange`, `MoveType`, `Rename`, `RefactoringStressTest`,
  `NarrowCheckStressTest`): #57 lambdas changed with their method, #58
  `move_type`'s unused import, #59 one "files that hold the name" walk, #60
  stress targets weighted, #61 stress restore after a killed run.
- **Cloud session** (the edit pipeline, the server and the structure:
  `Batch`, `Gate`, `Stubs`, `Server`, `SourceProject`, `SessionLog`,
  `SessionReport`, `Tools`, `Extract`, `Declarations`): #62 stubs per batch
  step, #63 the server's classpath cache, #64 a parent pom and the classpath
  cache, #34 one `ObjectMapper`, #32 the rest of the large-class splits
  (`Extract`, `Declarations`, `Tools`). (#46, first on this list, was done
  by the cloud session before the split was merged.)

The two sets share no class.

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
