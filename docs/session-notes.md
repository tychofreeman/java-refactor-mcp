# Session notes

What past sessions learned. Append a short dated entry when a session ends.
Keep entries to what the next session needs: decisions, surprises, what to try next.

## 2026-11-02: scaffold

- Created the project: Maven, JDK 21, `pom.xml` with JDT Core and the MCP Java SDK.
- The build sandbox could not reach Maven Central (`repo1.maven.org` returned 403
  from the egress proxy). The pom's versions are unverified, and nothing has been
  compiled or resolved yet. The first session with Maven Central access should run
  `mvn verify` and record the result here.
- Chose JDT Core headless over JavaLens (which returns edits instead of applying
  them). Reasons are in `docs/design.md`.
- Next: the JDT spike in `docs/design.md`, open question 1.
