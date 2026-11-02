# Source roots and the classpath from Maven

- **Item:** roadmap #7, first of the eight items the user asked for in order
  (positions 1-8 of the evidence order, 2026-11-02).
- **Date:** 2026-11-02
- **Branch:** source-roots-classpath

## Plan

At the highest level: a project is not "every `.java` file under a directory".
It is a set of source roots (what the gate checks and where types are found)
and a classpath (where library types are found), and for a Maven project both
come from its build. Today `SourceProject` has a root and a classpath nobody
fills, so on this repo the gate reads the golden fixtures as sources and cannot
resolve JDT, MCP or JUnit types.

Choices (defaults; recorded in `docs/design.md`):
- Maven first: `pom.xml` at the root means source roots `src/main/java` and
  `src/test/java` (those that exist), and the classpath from Maven itself:
  `dependency:build-classpath` with test scope, run by the project's `./mvnw`,
  else `mvn` on PATH, its output kept off stdout. Not chosen: reading `pom.xml`
  by hand (no transitive dependencies), embedding Maven Resolver (a large
  dependency for one call). Gradle, multi-module builds and non-standard source
  directories wait for a need.
- No build file, or Maven fails: the root is the one source root and the
  classpath is empty (today's behavior), with a line on stderr saying why.
- Paths in tool arguments stay relative to the project root.

Derivation, in TDD's order:
1. Green, make the change easy: `SourceProject(root, sourceRoots, classpath)`;
   `of(root)` keeps `[root]`. `javaFiles()` walks the source roots, `Jdt.parse`
   passes them as the source path. Callers: `GoldenTest`, `ServerTest` and
   `Server` call `of`, so nothing else changes. Green.
2. Red: `SourceProjectTest`:
   - `load` of a temporary Maven layout with no dependencies: source roots are
     `src/main/java` and `src/test/java`; `javaFiles` leaves out
     `src/test/resources`.
   - `load` of this repo: the classpath holds the JDT Core jar, and
     `TypeCheck.errors` is empty, so the gate sees this repo as compiling.
   - `load` of a directory with no `pom.xml` is `of`.
   `load` a stub returning `of(root)`.
3. Green: `load(root)`: the layout, then Maven for the classpath, through a
   temp output file.
4. `Server.main` uses `load` and reports what it loaded on stderr.
5. Docs: design (decision), contract (nothing changes in tool arguments),
   CLAUDE.md (the weaker-gate paragraph goes), session notes, roadmap #7 Done.

The behavior change is step 3; step 4 exposes it.

## Notes

- `read:` `SourceProject` and `TypeCheck.errors` through `inspect` (outline,
  then the bodies to change): the first reads through the server in this
  project.
- `hand:` `SourceProject` gained a `sourceRoots` component and `javaFiles` walks
  it; `Jdt.parse` passes it as the source path. Adding a record component and
  its uses is add_field plus replace_expression in gorefactor's terms; neither
  exists here. Green after it (12 golden, 7 server).
- `plan:` found while loading the tools: `rename_local`'s description has runs
  of spaces ("only       when"); a text block's `\` line continuation keeps the
  next line's indentation. A separate small fix after this item; logged in the
  friction log.

- `hand:` `SourceProjectTest` and the `load` stub; red for the right reasons
  (roots were `[root]`, classpath empty).
- `bulk:` `MavenBuild` (~100 lines), new behavior; `load` (~20 lines), new.
- `hand:` `Jdt.parse`: one encoding per source root, after
  `thisRepoTypeChecksClean` failed with `IllegalArgumentException` from
  `ASTParser.setEnvironment` (it wants the arrays the same length). Found by the
  test, which is what it was for.
- `hand:` `Server.main` calls `load` and says on stderr what it loaded.
- `plan:` added a check not in the plan: the server on a full copy of this repo
  (with `mvnw`): 2 source roots, 41 classpath entries; a clash refused, a
  rename applied; 4.7 s including Maven.

## Measured

Not measured (roadmap #18). From the notes: every read of this repo's Java went
through `inspect` (5 calls; no Read of a `.java` file). Four hand edits to
existing Java, two `bulk:` writes of new code, one new test file. No
`rename_local` call on this repo's own code: no rename was needed.

## Assessment

- **Through the tools or around them:** reads through the tools for the first
  time; edits around them, since there are no edit tools. Each hand edit is
  named in the friction log with the tool that would have done it.
- **Large writes:** `MavenBuild` and `load`, both new behavior.
- **Did the plan hold?** Yes, in its order. One bug it did not foresee (JDT's
  encodings array) was caught by the test written in step 2.
- **Reading:** outlines, then only the bodies being changed.
- **One change that would have helped:** the edit tools (roadmap #19, now
  *Next*); with them, the four hand edits were each one call.

## Follow-ups

- Fix the runs of spaces in `rename_local`'s description, with a test over
  every tool description (small; before #19, or as its first step).
- Gradle, multi-module Maven, non-standard source directories; reload when
  `pom.xml` changes (open question 2's remainder).
- Loading costs a Maven run at each server start (about 3 s); cache by
  `pom.xml`'s time if it starts to matter.
