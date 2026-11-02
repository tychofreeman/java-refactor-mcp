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
- Chose JDT Core headless over JavaLens (which returns edits instead of applying
  them). Reasons are in `docs/design.md`.
- Next: the JDT spike in `docs/design.md`, open question 1.
