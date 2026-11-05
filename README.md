# java-refactor-mcp

An MCP server that gives Claude behavior-preserving refactorings for Java,
built on Eclipse JDT Core. It is the Java counterpart to
[go-refactor-mcp](https://github.com/tychofreeman/go-refactor-mcp) (gorefactor).

The server runs over STDIO and serves one project. Its tools read Java
(`inspect`), make refactorings (renames, signature changes, extract and inline,
moves between files and types, conditionals, encapsulation) and edits, all
type-checked before and after, with `batch` and `undo`. The tools and their
arguments are in [docs/tool-contract.md](docs/tool-contract.md); `bin/jrefactor`
runs the same tools from a shell, one call per run (`bin/jrefactor list`). The
roadmap is in [docs/design.md](docs/design.md), and how the project was built in
[docs/how-this-project-was-built.md](docs/how-this-project-was-built.md).

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

- A JDK 21 (with `javac`; a JRE is not enough), as `JAVA_HOME` or `java` on `PATH`
- Network access to Maven Central for the first build. Maven itself comes from
  the wrapper (`./mvnw`), pinned to 3.9.16.

## Build and test

```sh
./mvnw verify
```

## Running

Every change to `main` that passes CI is released: download `java-refactor-mcp.jar`
from the latest [release](https://github.com/tychofreeman/java-refactor-mcp/releases), or
build it with `./mvnw package` (`target/java-refactor-mcp.jar`). The server reports the
release it was built as, or `devel`. It serves one project, the directory given as its
argument (default: the working directory):

```sh
claude mcp add jrefactor -- java -jar /path/to/java-refactor-mcp/target/java-refactor-mcp.jar /path/to/project
```

Or use the launcher, which builds the jar first when it is missing or stale
(it needs a JDK: `JAVA_HOME`, or `javac` on `PATH`):

```sh
claude mcp add jrefactor -- /path/to/java-refactor-mcp/bin/jrefactor-mcp /path/to/project
```

In this repo, `.mcp.json` runs the launcher on the checkout itself. The server
exits when its input ends, after answering the requests it has read.

## Layout

```
pom.xml                      Maven build; JDT Core and MCP SDK dependencies
src/main/java/.../jrefactor  server (Server), tools (Inspect, Rename, ...) and the gate (TypeCheck)
src/test/java/.../jrefactor  unit and golden-case tests
src/test/resources/golden    golden cases: in/, args.properties, and out/, result.txt or refused.txt
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
