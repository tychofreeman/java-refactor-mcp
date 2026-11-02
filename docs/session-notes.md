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
- Sandbox Maven needs `~/.m2/settings.xml` with the egress proxy from
  `$HTTPS_PROXY` and a `central` mirror at `https://repo1.maven.org/maven2`.
  Without it Maven tries `repo.maven.apache.org`, which does not resolve there.
- Chose JDT Core headless over JavaLens (which returns edits instead of applying
  them). Reasons are in `docs/design.md`.
- Next: the JDT spike in `docs/design.md`, open question 1.
