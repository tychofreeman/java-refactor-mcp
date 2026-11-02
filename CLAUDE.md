# CLAUDE.md

Instructions for Claude Code in this repository. Read this first.

## What this repo is

`java-refactor-mcp`: an MCP server that performs behavior-preserving Java
refactorings using Eclipse JDT Core, for Claude to call during feature work.
Companion to gorefactor (`tychofreeman/go-refactor-mcp`). Keep Java-specific
decisions here; keep the shared tool contract in `docs/tool-contract.md`.

Current state: scaffold. No tools exist yet. Check `docs/design.md` for what is
decided and what is open before starting work.

## Build and test

```sh
mvn verify          # compile, run unit and golden tests
mvn -q test         # quieter, tests only
```

- JDK 21, Maven 3.9+.
- The pom's JDT and MCP SDK versions are marked UNVERIFIED. The first task in a
  session that can reach Maven Central is to confirm them, then remove the note.

## Working rules

- **Coordinates are 1-based** for lines, matching gorefactor and `inspect`-style
  outlines. Columns are 1-based too unless the contract says otherwise.
- **Every refactoring type-checks before and after.** A change that introduces a
  compile error is rolled back and reported, never left applied.
- **Refusals are model-facing.** Each refusal names the reason and the fix the
  model should try next, in plain words. Refusal wording is part of the contract.
- **Golden cases first.** Add `src/test/resources/golden/<case>/{in,out}` (plus
  `args.json` or `refused.txt`) before or with the code that makes it pass.
- **No JDT UI or LTK.** Use JDT Core directly. Do not depend on the Eclipse
  workbench, OSGi runtime or a workspace on disk.
- **Do not reprint the AST.** Edits are text edits against the original source,
  so comments and formatting survive.
- **Small results.** Results report what changed, not the whole file. Measure
  before adding fields.

## Documentation to keep in sync

- `docs/tool-contract.md`: update when a tool name, argument or result shape changes.
- `docs/design.md`: record each decision that changes the architecture.
- `docs/session-notes.md`: append what a session learned, briefly, when it ends.
- `docs/friction-log.md`: log each time a tool got in the way or a refactoring
  was done by hand. The roadmap is ordered by this log, as in gorefactor.

## Things not to do

- Do not add a dependency on Eclipse UI, LTK or the workbench.
- Do not write into the user's source tree from a query-only tool.
- Do not add tools speculatively. Add one when the friction log or a task needs it.
- Do not commit generated files, `target/`, or IDE metadata.

## Shared with gorefactor

The tool contract and the A/B measurement harness are meant to be shared with
gorefactor. Until that happens, changes to shared behavior go into
`docs/tool-contract.md` first, then into both codebases.
