# Sessions here use the server on this checkout

- **Item:** roadmap #13, "build this project through its own tools:
  `.mcp.json` running this checkout; hand edits logged". The user said "go
  ahead with #13" (2026-11-02). Both earlier records ask for it.
- **Date:** 2026-11-02
- **Branch:** mcp-json-self

## Plan

At the highest level: a Claude Code session in this repo should start the
server built from this checkout, serving this checkout, as gorefactor's
`.mcp.json` runs `go run ./cmd/refactor-mcp`. Java needs a build step that
`go run` hides, so the missing piece is a launcher that builds when the jar
is stale and keeps the build's output off stdout (stdout is the protocol).

Measured before planning, on a `git archive` copy of the repo (30 Java
files): `inspect` of `Positions.java` and `rename_local` of a local in
`Inspect.file` both answered correctly in about a second. The gate is weaker
here than on the fixtures: with the repo root as the only source path and no
classpath, JDT and MCP types do not resolve, so their errors are in the
"before" counts. Rename of a local still compares counts per file and
message, so an added error still shows. Fixing source roots and the classpath
is roadmap #7, not this item.

Steps:
1. `bin/jrefactor-mcp [project root]`, POSIX sh:
   - project root defaults to the checkout;
   - JAVA_HOME, if unset, comes from the `javac` on PATH (this machine's
     default `java` is a JRE 25 with no compiler, which fails the build as
     "release version 21 not supported");
   - if the jar is missing or older than `pom.xml` or anything under
     `src/main`, run `./mvnw -q -B package -DskipTests` with its output sent
     to stderr; a failed build exits non-zero with Maven's message on stderr;
   - then `exec java -jar target/java-refactor-mcp.jar <root>`.
2. `.mcp.json`: one server, `jrefactor`, command `bin/jrefactor-mcp`.
3. Check: a stale jar is rebuilt and the server then answers (initialize
   and an `inspect` call over stdio); a fresh jar is not rebuilt; a `claude
   -p` probe with this repo's `.mcp.json` lists both tools.
4. `CLAUDE.md`: a short "Using java-refactor-mcp" section, from gorefactor's
   "Using gorefactor well", trimmed to the two tools there are: load both
   schemas in one ToolSearch; read `.java` through `inspect` (outline, then
   line ranges) rather than Read; rename locals by name; a session's tools
   are the build from when it started, so test server changes with `./mvnw
   test`, and restart Claude Code after merging one.
5. README: the launcher for other projects. Roadmap #13 Done; session notes.

No Java changes are planned, so no TDD steps; the launcher is checked by
running it (step 3).

## Notes

- `hand:` the session record (docs are text in any case).
- `read:` none of the Java; the measurement above called the tools.
- `hand:` `bin/jrefactor-mcp` and `.mcp.json`; not Java. A stale jar was rebuilt
  with `JAVA_HOME` unset and the server then listed both tools; a fresh jar was
  not rebuilt. The rebuild takes about 4 s.
- `plan:` timing the rebuild showed the server never exits when stdin ends
  (`main` waited on a latch nothing counted down). A gorefactor lesson is to
  check that long-running processes stop, so this was added to the item.
- `hand:` `ServerTest.endOfInputIsReportedOnce` (red on a stub), then
  `Server.endingWith` and `main` exiting at the end: new behavior.
- `plan:` exiting at once lost every reply of a piped script (0 of 2 lines). A
  wait after the end, for calls and a quiet second, still lost the tool call
  (1 of 2): the SDK closes the session when it reads the end and drops what it
  has read but not run. So the read that finds the end now waits (`awaitQuiet`)
  before returning it. Checked by running the jar: 2 of 2 replies, exit about
  1 s after the last, three runs; and a 5 s `rename_local` on a 390-file copy
  of this repo answered before the exit. This timing logic has no unit test,
  only these runs.
- `plan:` a `claude -p` probe from the repo with `--mcp-config .mcp.json` and
  `JAVA_HOME` unset listed `inspect` and `rename_local` and called `inspect`
  (Haiku, 3 turns, $0.004).

## Measured

Not measured (no session-log hook, roadmap #18). From the notes: three changes
to Java, all new behavior (`endingWith`, `awaitQuiet` and `main`, the call
counter in `answer`), one new test; no java-refactor-mcp calls in this session.

## Assessment

- **Through the tools or around them:** around; this session started before
  `.mcp.json` existed. The Java written was new behavior, not existing code
  moved, so the tools there are would not have covered it anyway.
- **Large writes:** one, `endingWith` (about 35 lines with formatting, in the
  same call as `main`'s change): new behavior, no existing code written again.
  It was not noted as `bulk:` when it was written.
- **Did the plan hold?** The launcher and `.mcp.json` held. The plan missed the
  exit at the end of input, found by timing; it took two wrong attempts
  (exiting at once, then waiting after the SDK had seen the end) before the
  fix that holds the end back. Both failures were found by running the jar,
  not by tests.
- **Reading:** no Java read to find anything; parts of `Server` were printed to
  place the edits.
- **One change that would have helped:** a test that runs the jar as a process
  with a piped script and counts the replies. The end-of-input behavior is
  only checked by hand now. Follow-up below.

## Follow-ups

- A process-level test of the server (pipe a script into the built jar, count
  replies, check the exit), perhaps as a Maven integration test after
  `package`.
- Source roots and classpath (roadmap #7), so the gate resolves this repo's
  own types.
- The read guard (roadmap #15) now has something to send reads to.
