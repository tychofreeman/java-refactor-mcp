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

**Decided: MCP over STDIO with the official Java SDK** (`io.modelcontextprotocol.sdk:mcp-core`).

**Decided: 1-based lines** in every tool argument and result, matching gorefactor
and `inspect`-style outlines.

**Decided: Maven.** Group `io.github.tychofreeman`, artifact `java-refactor-mcp`,
JDK 21.

**Decided: separate repos** for the Go and Java servers. A shared repo for the
tool contract and the A/B harness is planned once a second language exists.

## Open questions

1. **Running JDT Core without OSGi.** JDT Core on Maven Central does not bring the
   whole Eclipse platform. The plan is headless `ASTParser` with an explicit
   classpath (`setEnvironment`) and `setResolveBindings(true)`. This is not yet
   verified: check whether it needs `org.eclipse.core.runtime`/`resources` jars,
   and whether bindings resolve across the project's classpath without a
   workspace. A spike should answer this before any refactoring is written.
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

1. JDT spike: parse one file with bindings resolved against a classpath,
   without a workspace. Record what is needed.
2. Start the MCP server over STDIO with one tool (`inspect`-equivalent: outline
   with 1-based lines). Check that it lists and answers from Claude Code.
3. Add `rename` with golden cases, type-check before and after, and rollback.
4. Run the A/B harness on a small Java task set against a stock baseline.
