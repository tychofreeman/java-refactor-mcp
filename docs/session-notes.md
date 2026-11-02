# Session notes

What sessions working on this project have learned that the code, the README and
the other docs do not record: the user's preferences, why past choices were made,
and where the work stands. Every session reads this (CLAUDE.md imports it). When
you learn something a later session would need, add it here in the same PR as the
work, and correct or remove what has become wrong. Keep it short: facts, with the
reason, not a diary. The roadmap is in `docs/design.md`.

## Working with the user

- One item per branch and PR. Adopted from gorefactor on 2026-11-02, after the
  first items had been committed straight to `main`.
- Claude merges its own PRs once CI is green and they are mergeable (the user,
  2026-11-02); use merge commits, as earlier PRs did.
- Stacked PRs: retarget the upper PR to `main` *before* merging the lower one
  with `--delete-branch`. Deleting the base branch first closed #4, and a closed
  PR whose base is gone cannot be reopened or retargeted until the branch is
  pushed back (2026-11-02).
- Development standards follow gorefactor's (`CLAUDE.md` there, and its
  `docs/how-this-project-was-built.md`). The user asked for the same standards
  here (2026-11-02), including the same goal: about 95% of coding through the
  tools, judged by token cost against editing by hand.
- "What's next?" runs the `whats-next` skill, ported from gorefactor on
  2026-11-02. In gorefactor the user chose a record per item in
  `docs/sessions/` so they can judge whether a session used the tools or worked
  around them. Here it has no session-log hook yet (roadmap #18), so the notes
  are the whole record.

## Where the work stands

- Built: the type-check gate and `rename_local` (locals and parameters). Layers:
  `SourceProject` (root and classpath), `Jdt` (headless parse), `TypeCheck`
  (project-wide compile-error counts before and after, the gate every
  refactoring uses), `Refusal`, `Positions` (1-based to offset), `RenameLocal`.
  Four golden cases.
- The MCP server runs (roadmap #5, 2026-11-02): `Server` over STDIO, one
  project root per process, one tool, `inspect`. Claude Code lists and calls it
  (`claude -p --mcp-config` probe with Haiku, $0.0008).
- `rename_local` is a tool too (roadmap #6, 2026-11-02), addressed by `name` and
  `line` as gorefactor's `rename` is; `column` only for two meanings on one line.
  A Haiku probe renamed a constructor parameter through it with no column.
- Sessions here have the tools (roadmap #13, 2026-11-02): `.mcp.json` runs
  `bin/jrefactor-mcp`, which rebuilds a stale jar (about 4 s, output to stderr)
  and serves this checkout. Restart Claude Code after merging a server change.
- The server exits when stdin ends, but only after it is quiet: the SDK closes
  the session as soon as it reads the end of input and drops requests it has
  read but not run, so the read that finds the end waits until no tool call is
  running and nothing was written for a second. Exiting at once lost every
  reply of a piped script; never exiting leaves a JVM behind a crashed client.
- Maven projects load from their build (roadmap #7, 2026-11-02): source roots
  `src/main/java` and `src/test/java`, and the test classpath from
  `dependency:build-classpath`. This repo now type-checks with zero errors in
  the gate (`SourceProjectTest.thisRepoTypeChecksClean`); loading it costs a
  Maven run, about 3 s, at each server start. JDT's `setEnvironment` needs one
  encoding per source path entry, or it throws `IllegalArgumentException`.
- The first edit tools exist (roadmap #19, 2026-11-02): `create_file`,
  `replace_text`, `add_declaration` (replaces "not implemented" stubs),
  `replace_declaration`. Tool definitions live in `Tools.ALL`; `GoldenTest` calls
  them the same way the server does, with `<arg>.txt` files for long arguments.
- Edits fix imports (roadmap #20, 2026-11-02): `Imports.fix` runs before the
  gate in `replace_text`, `add_declaration` and `replace_declaration`. The class
  index (JDK through `jrt:/`, classpath jars) is built once per server and
  classpath, the sources each call; a server start plus two import-fixing edits
  took 2.2 s.
- The server runs one tool call at a time (`Server.ONE_AT_A_TIME`, 2026-11-02).
  Before that, two edits sent together ran at once and the second undid the
  first (found by a piped probe; `ServerTest.editsSentTogetherBothApply`).
- `batch` and `undo` exist (roadmap #8, 2026-11-02). Every write goes through
  `Gate` (`apply`, or `write` for non-Java) and is recorded by `Journal`; a
  top-level call is `Tools.call`, which begins and commits the journal entry.
  A Haiku probe chose `batch` with check `"end"` for a signature change and its
  caller unprompted (6 turns, $0.004).
- The client keeps tool descriptions from before a restart in its listing (seen
  twice: after #19 and #20); the server's are current. Not this server's bug.
- Two `replace_text` "old is not in" refusals (#20, #8) were the model reading
  `inspect`'s two-space separator as indentation.
- Writing Java through a Python script: use raw strings. In a plain Python
  string a backslash before a newline joins the lines, which is how
  `rename_local`'s description got its runs of spaces. The Edit tool avoids it.
- The user asked for roadmap positions 1-8 in order, one PR each (2026-11-02):
  #7, #19, #20, #8, #21, #22, #23, #18.
- The roadmap's open items are ordered by gorefactor's evidence (the user asked,
  2026-11-02, after items had been ordered only by what unblocks what): source
  roots and classpath (#7), then the first edit tools, imports, undo and batch,
  stubs, signature changes. The reasons are under "Why this order" in
  `docs/design.md`.
- The SDK (2.1.0) was checked with `javap` against the jars, not from memory:
  `Tool.builder()` is deprecated in favor of `Tool.builder(name, mapper, schema)`,
  and the SDK validates arguments against the input schema itself.
- To test a server change from Claude Code without registering it, write a
  `--mcp-config` JSON in the scratchpad and run `claude -p "<prompt>" --model
  haiku --strict-mcp-config --mcp-config <file> --allowedTools mcp__<name>__<tool>`.
  Put the prompt first: `--allowedTools` takes several values and swallows it.
  Redirect stdin (`< /dev/null`): otherwise it waits 3 s and prints a warning
  into the output, which then does not parse as JSON (the cause of two "not
  JSON" probe outputs, in #6 and #19).
- `rename_local` is a Java-specific name; gorefactor's `rename` covers more
  kinds of declaration.

## Why past choices were made

- JDT Core headless, not JavaLens: JavaLens returns edits for the model to apply,
  and this project applies them itself, as gorefactor does. Details in
  `docs/design.md`.
- The JDT spike (`spikes/jdt-headless/`) showed bindings resolve with no
  workspace and no OSGi start, against a source path and a classpath.

## Build environment

- Sandbox Maven needs `~/.m2/settings.xml` with an active `https` proxy at the
  host and port in `$HTTPS_PROXY`; without it Maven fails with "Name or service
  not known" for `repo.maven.apache.org`, even when `curl` reaches it. The port
  changes between sessions and once went stale mid-session, so regenerate the
  file before every Maven run. No mirror is needed.
- Maven Central answered 429 (rate limit) once; the rerun passed.
- Maven Wrapper added 2026-11-02 (the user's choice over apt, a tarball in
  `~/.local`, or Docker) when the user's own machine had no Maven: one pinned
  Maven everywhere, CI included. That machine also had only JRE packages
  (`openjdk-21-jre`), which fails as "release version 21 not supported"; the fix
  is `openjdk-21-jdk-headless`. Its default `java` is 25, so set
  `JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64`.
- CI: `./mvnw verify` on JDK 21 plus a `spotless:check` job; runs are skipped for
  docs-only changes. Dependabot opens weekly PRs for Maven and Actions. As of
  2026-11-02: JUnit 6.1.3, `actions/checkout@v7`, `actions/setup-java@v6`. The
  first runs on `main` passed (2026-11-02).
