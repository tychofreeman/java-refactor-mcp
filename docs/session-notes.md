# Session notes

What sessions working on this project have learned that the code, the README and
the other docs do not record: the user's preferences, why past choices were made,
and where the work stands. Every session reads this (CLAUDE.md imports it). When
you learn something a later session would need, add it here in the same PR as the
work, and correct or remove what has become wrong. Keep it short: facts, with the
reason, not a diary. The roadmap is in `docs/design.md`.

## Working with the user

- One item per branch and PR; the user merges. Adopted from gorefactor on
  2026-11-02, after the first items had been committed straight to `main`.
- Development standards follow gorefactor's (`CLAUDE.md` there, and its
  `docs/how-this-project-was-built.md`). The user asked for the same standards
  here (2026-11-02), including the same goal: about 95% of coding through the
  tools, judged by token cost against editing by hand.

## Where the work stands

- Built: the type-check gate and `rename_local` (locals and parameters). Layers:
  `SourceProject` (root and classpath), `Jdt` (headless parse), `TypeCheck`
  (project-wide compile-error counts before and after, the gate every
  refactoring uses), `Refusal`, `Positions` (1-based to offset), `RenameLocal`.
  Four golden cases.
- Next: the MCP server over STDIO (roadmap #5). `mcp-core` 2.1.0 is on the
  classpath but unused; check its API against the jar first.
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
- CI: `mvn verify` on JDK 21 plus a `spotless:check` job; runs are skipped for
  docs-only changes. Dependabot opens weekly PRs for Maven and Actions. As of
  2026-11-02: JUnit 6.1.3, `actions/checkout@v7`, `actions/setup-java@v6`. The
  first runs on `main` passed (2026-11-02).
