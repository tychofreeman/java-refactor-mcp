# MCP server over STDIO with a read-only `inspect`

- **Item:** roadmap #5, chosen by the user from "what's next?" (2026-11-02).
  It unblocks #6 (wire `rename_local`), #11 (A/B), #13 (build through the
  tools), #15 (read guard) and #18 (session log).
- **Date:** 2026-11-02
- **Branch:** mcp-server-inspect

## Plan

At the highest level: the library has operations but no way for a model to
call them, so this adds the boundary, a process that speaks MCP on
stdin/stdout for one project root, and the first thing worth calling
through it, a read that gives line numbers for later refactorings. Reads
are a query over source text and need no bindings and no gate.

What the SDK offers (checked with `javap` against the 2.1.0 jars, not
from memory):
- `mcp-core` has no JSON binding; `McpJsonDefaults.getMapper()` finds one
  by ServiceLoader. The `io.modelcontextprotocol.sdk:mcp` artifact is
  `mcp-core` plus `mcp-json-jackson3`, at the same version. Depend on
  `mcp` in place of `mcp-core`.
- `McpServer.sync(new StdioServerTransportProvider(mapper, in, out))`
  `.serverInfo(name, version).capabilities(ServerCapabilities.builder()`
  `.tools(false).build()).toolCall(Tool, (exchange, CallToolRequest) ->`
  `CallToolResult).build()`. `Tool.builder().name().description()`
  `.inputSchema(mapper, jsonString).annotations(...)`.
  `CallToolResult.builder().addTextContent(s).isError(b)`.
  The in/out constructor lets a test drive the server in process.

`inspect`, first version, in gorefactor's text format (the contract says
to copy it unless measured otherwise):
- `file`: the file with method, constructor and initializer bodies elided:
  `NN  <line>` for each kept line, and in place of a body
  `… lines a-b   [n lines]`. Complexity, nesting and side effects, which
  gorefactor adds, wait for evidence that they help here.
- `file` + `start_line`/`end_line`: those lines, numbered.
- A missing file, or a range outside the file, is a refusal that says
  what exists (the file's line count; the nearest Java files).
Not in this PR: no-argument overview, `function`, `targets`, `names`,
`uses`.

Derivation, in TDD's order:
1. Green, make the change easy: `GoldenTest` runs only `RenameLocal`.
   Give `args.properties` a `tool` key (default `rename_local`, so the four
   cases need no change), and let a case hold `result.txt` (the text a
   query tool returns) in place of `out/`. Tests stay green. Then:
   `pom.xml` `mcp-core` -> `mcp`. Still green.
2. Red: golden `inspect_outline` (a class with a field, a constructor, two
   methods, an inner class, comments), `inspect_lines`, and
   `inspect_refused_missing_file`; `Inspect` as a compiling stub
   throwing `UnsupportedOperationException`.
3. Green: `Inspect.file(project, file)` and `Inspect.lines(...)`: parse
   with `Jdt.parse` (without needing bindings), collect body ranges of
   method, constructor, initializer and lambda-free declarations by
   visitor, print lines skipping ranges strictly inside each body's
   braces.
4. Red: `ServerTest`, in process: piped streams, send `initialize`,
   `notifications/initialized`, `tools/list` and a `tools/call` of
   `inspect`, and assert the tool is listed (read-only annotation) and the
   call returns the outline; a refusal comes back with `isError` true and
   the refusal's text. Then `Server` stub.
5. Green: `Server.main(args)`: project root from `args[0]` (default the
   working directory), `Server.start(project, in, out)` builds the
   sync server. A `Refusal` becomes an error result with its message; any
   other exception an error result marked as an engine error (contract,
   "Results").
6. Packaging, so Claude Code can start it: shade plugin to
   `target/java-refactor-mcp.jar` (strip jar signatures), and check by
   hand that `claude mcp` lists `inspect` and a call answers. Logging must
   not reach stdout.
7. Docs: `docs/tool-contract.md` gains `inspect` as built; roadmap #5
   *Done*; session notes "Where the work stands".

The one behavior change is step 3 (and its wiring in 5); everything else
prepares or exposes it.

## Notes

- `hand:` the session record, before the server exists; docs are text in any case.
- `read:` GoldenTest's body, to give it a `tool` key; reading to change it, which is expected.
- `hand:` GoldenTest: `call(tool, project, args)` extracted from `runCase` and a `result.txt` branch added; extract_function (gorefactor's name) would have done the extraction, the new branch is new behavior.
- `hand:` `pom.xml` `mcp-core` -> `mcp`; not Java, a text replacement (gorefactor's replace_text).
- `hand:` four golden cases for `inspect` (inputs, `result.txt`, `refused.txt`); fixtures, written as text (create_file).
- `hand:` `Inspect` stub, then GoldenTest's `inspect` case in `call`; create_from_usage would have made the stub from the test's calls.
- `read:` `Jdt.parse`'s body, to decide whether a read can reuse it: it resolves bindings, which a read does not need; reused anyway as the least code (follow-up).
- `bulk:` `Inspect` written whole (~170 lines after formatting): new behavior, nothing existing typed again; replace_declaration of the stub's two bodies plus add_declaration of the helpers would have been the tool route.
- `plan:` before the plan's step 1, Maven was missing on this machine, and then
  `javac` (only JRE packages). The user chose the Maven Wrapper (its own PR, #7) and
  installed `openjdk-21-jdk-headless`; this branch was recreated on the new main.
- The notes below, from `ServerTest` on, were written at the end of the work,
  not as it happened, contrary to the skill. Each one is checked against the
  commands in the transcript.
- `bulk:` `ServerTest` (~130 lines) and `Server` (~150 lines), each written whole:
  new behavior. The `Server` stub came first (create_from_usage's job).
- `hand:` `Tool.builder()` -> `Tool.builder(name, mapper, schema)` after
  `-Werror` refused the deprecated call; a 5-line Edit (replace_expression).
- `plan:` the smoke test over real stdio showed that the SDK validates arguments
  against the schema before the handler runs, and its message for an unknown
  argument does not name the ones to use. A `ServerTest` case went red first,
  then `"additionalProperties": false` came out of the schema so `checkArgs`
  answers. Recorded in `docs/design.md`.
- `hand:` `pom.xml` shade plugin (packaging; not Java).
- `plan:` step 6's "check by hand" became a `claude -p` probe with a scratch
  `--mcp-config` (Haiku, $0.0008, 3 turns). Claude Code listed and called
  `inspect` and read the elided range correctly.

## Measured

Not measured (no session-log hook, roadmap #18). Counts from the notes: about
nine hand changes to Java (two of them existing code: the `call` extraction and
the builder call), three `bulk:` writes (`Inspect`, `ServerTest`, `Server`), all
new behavior; no java-refactor-mcp calls and no refusals, since the server did
not exist until the end.

## Assessment

- **Through the tools or around them:** around, necessarily: there was no
  server. The two edits of existing code (extracting `GoldenTest.call`, the
  builder call) are what extract_function and replace_expression would have
  done. Everything else was new behavior.
- **Large writes:** three, all new code, none typing existing code out again.
  With tools, the route would have been stubs from the tests (create_from_usage),
  then replace_declaration per body; with no tools, writing them out was the
  only route.
- **Did the plan hold?** Mostly. The operations it named happened in its order,
  with one step added (unknown arguments, from the smoke test) and step 6
  changed from a manual check to a probe. The `mcp` artifact choice and the
  output format held as planned.
- **Reading:** two bodies (`GoldenTest`, `Jdt.parse`), both read to change or
  reuse them; no reading to find uses.
- **What went badly:** notes from `ServerTest` on were not written as they
  happened. And `Inspect` reuses `Jdt.parse`, which resolves bindings a read
  does not need (a follow-up below).
- **One change that would have helped:** the server existing, which this item
  delivers. Next is #6, so the next item's edits can go through it. Friction-log
  entry: "the first server and `inspect`, all by hand".

## Follow-ups

- Roadmap #6: wire `rename_local` into `Server`, with the same `refused:` path.
- `Inspect` parses with bindings (`Jdt.parse`); a syntax-only parse would be
  cheaper on large projects. Measure with #10 before changing it.
- `inspect` features gorefactor has and this lacks: per-body complexity, nesting
  and side effects; no-argument overview; `function`, `targets`, `names`,
  `uses`. Add each when a friction-log entry asks for it.
- `.mcp.json` running this checkout's jar, so sessions here can use `inspect`
  (roadmap #13).
