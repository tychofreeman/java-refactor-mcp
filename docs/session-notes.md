# Session notes

What past sessions learned. Append a short dated entry when a session ends.
Keep entries to what the next session needs: decisions, surprises, what to try next.

## 2026-11-02: scaffold

- Created the project: Maven, JDK 21, `pom.xml` with JDT Core and the MCP Java SDK.
- The build sandbox could not reach Maven Central at first (`repo1.maven.org`
  returned 403 from the egress proxy). After the egress settings were changed,
  Maven Central resolved. The pom's versions were checked against it:
  JDT Core 3.47.0, MCP SDK 2.1.0 and JUnit 5.14.4. `mvn verify` passes on the
  scaffold (no tests yet).
- Sandbox Maven needs `~/.m2/settings.xml` with an active `https` proxy set to the
  host and port in `$HTTPS_PROXY`. Without it Maven fails with "Name or service not
  known" for `repo.maven.apache.org`, even when `curl` reaches it through the proxy.
  The proxy port changes between sessions, so regenerate the file each session.
  No mirror is needed. (A `repo1.maven.org` mirror was tried first; it was unnecessary.)
- JDT spike done (`spikes/jdt-headless/`): bindings resolve headless against a
  source path and a classpath, with no workspace and no OSGi start. Design open
  question 1 is answered. Next: the after-edit type check, then the first tool.
- Built the first refactoring, `rename_local` (Java-specific name; gorefactor's
  `rename` covers more). Layers: `SourceProject` (root and classpath), `Jdt`
  (headless parse), `TypeCheck` (project-wide compile-error counts before and
  after, the gate every refactoring uses), `Refusal`, `Positions` (1-based to
  offset), `RenameLocal`. Four golden cases pass: basic rename, refused field,
  refused name clash, refused edit that would break compilation.
- Not built yet: the MCP server wiring (`mcp-core` 2.1.0 is on the classpath, not
  used), fields and other kinds of rename, and build-tool classpath loading.
- Maven Central answered 429 (rate limit) on one run; the rerun passed. Not a
  permanent problem, but the build can need a retry.
- The proxy port in `~/.m2/settings.xml` went stale mid-session (46609 vs the old
  43251). Regenerate it from `$HTTPS_PROXY` before every Maven run.
- Chose JDT Core headless over JavaLens (which returns edits instead of applying
  them). Reasons are in `docs/design.md`.
- Next: the JDT spike in `docs/design.md`, open question 1.
