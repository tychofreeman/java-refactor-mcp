# java-refactor-mcp

An MCP server that gives Claude behavior-preserving refactorings for Java,
built on Eclipse JDT Core. It is the Java counterpart to
[go-refactor-mcp](https://github.com/tychofreeman/go-refactor-mcp) (gorefactor).

**Status: scaffold only.** The project layout, Maven build and documentation
exist. No tools are implemented yet.

## Goal

During ordinary feature work, Claude picks the refactorings that make a change
easy, the server performs them deterministically, and Claude makes the small
edit the change needs. Success is measured the same way as gorefactor: a task
done through the server should cost fewer tokens than the same task done by
hand, and should still pass the hidden tests.

## Approach

- **Engine:** Eclipse JDT Core, headless. Compiler, AST, bindings and search.
- **Edits:** each refactoring returns and applies text edits against the original
  source, then type-checks before and after and rolls back on failure.
- **Interface:** MCP over STDIO, using the official Java MCP SDK.
- **Coordinates:** 1-based lines, as in gorefactor.

Design decisions and open questions are in [docs/design.md](docs/design.md).
The tool contract shared with gorefactor is in
[docs/tool-contract.md](docs/tool-contract.md).

## Requirements

- JDK 21 or later (`JAVA_HOME` set, or `java` on `PATH`)
- Maven 3.9 or later
- Network access to Maven Central for the first build

## Build and test

```sh
./mvnw verify
```

## Layout

```
pom.xml                      Maven build; JDT Core and MCP SDK dependencies
src/main/java/.../jrefactor  server code (empty so far)
src/test/java/.../jrefactor  unit and golden-case tests
src/test/resources/golden    golden cases: in/, out/ and args per case
docs/                        design, tool contract, session notes, friction log
CLAUDE.md                    instructions for Claude Code working in this repo
```

## Documentation

- [CLAUDE.md](CLAUDE.md): how Claude Code should work in this repo
- [docs/design.md](docs/design.md): architecture and decisions
- [docs/tool-contract.md](docs/tool-contract.md): tool names, arguments and results
- [docs/session-notes.md](docs/session-notes.md): what past sessions learned
- [docs/friction-log.md](docs/friction-log.md): where the tools got in the way

## License

MIT.
