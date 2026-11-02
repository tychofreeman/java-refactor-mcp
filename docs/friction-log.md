# Friction log

Log each time a tool got in the way, a refusal was unclear, or a refactoring had
to be done by hand. The roadmap is ordered by this log, as it is in gorefactor.

Format, one entry per event. Append new entries at the end of the file (oldest
first), so parallel PRs do not conflict at the same place:

```
## YYYY-MM-DD: short title
- Task: what the session was doing
- Tool: which tool (or none, if it was by hand)
- What happened: what got in the way, with the refusal or error text if any
- Cost: rough extra calls or tokens, if known
- Idea: what would have helped, if obvious
```

Before writing that no tool covers a change, look for a sequence of two or three
tools that composes to it, and name the sequences tried and why each failed.
When a later PR closes an entry, mark it *Follow-up: done* with the PR.

## 2026-11-02: the first server and `inspect`, all by hand
- Task: roadmap #5, the MCP server over STDIO with `inspect`
  (`docs/sessions/mcp-server-inspect.md`).
- Tool: none; the server did not exist until the end of the PR.
- What happened: every Java change was a hand edit. The ones a tool would have
  done: extracting `GoldenTest.call` from `runCase` (extract_function), the
  `Inspect` and `Server` stubs from the tests' calls (create_from_usage), and
  replacing the stubs' bodies (replace_declaration). The rest was new behavior.
- Cost: not measured (no session-log hook yet, roadmap #18).
- Idea: none new. The gap is the whole edit tool set, which the roadmap already
  orders: `rename_local` through the server (#6), then building this project
  through its own tools (#13).

## 2026-11-02: `rename_local` through the server, by hand
- Task: roadmap #6 (`docs/sessions/rename-local-tool.md`).
- Tool: none; this session's Claude Code has no java-refactor-mcp connected
  (no `.mcp.json` yet).
- What happened: two changes to existing Java that tools would have done:
  adding `name` to `RenameLocal.run` and its caller (add_parameter), and moving
  the column lookup out of `run` into `atColumn` (extract_function). The rest
  was new behavior. `inspect` existed, but was not callable from this session.
- Cost: not measured (roadmap #18).
- Idea: `.mcp.json` running this checkout's jar (roadmap #13), so the next item
  can read with `inspect` and rename with `rename_local`. Then add_parameter and
  extract_function are the edit tools this item would have used.

## 2026-11-02: `.mcp.json` for this checkout, by hand
- Task: roadmap #13 (`docs/sessions/mcp-json-self.md`).
- Tool: none in this session (it started before `.mcp.json` existed).
- What happened: the change to existing Java was `Server.main` (the exit at the
  end of input) and `answer` (counting calls); both new behavior, written by
  Edit. The launcher, `.mcp.json` and docs are not Java.
- Cost: not measured (roadmap #18).
- Idea: none; from the next session on, `inspect` and `rename_local` are there.

## 2026-11-02: source roots and the classpath, reads through `inspect`
- Task: roadmap #7 (`docs/sessions/source-roots-classpath.md`).
- Tool: `inspect` for every read of this repo's Java; no edit tools exist.
- What happened: edits to existing code by hand: a `sourceRoots` component on
  `SourceProject` and its uses (add_field, replace_expression), `Jdt.parse`'s
  environment (replace_statements), `Server.main` calling `load`
  (replace_expression). New code (`MavenBuild`, `load`, the test) by Write.
  Also: `rename_local`'s description, as a model sees it, has runs of spaces
  ("only       when"), because a text block's `\` continuation keeps the next
  line's indentation.
- Cost: not measured (roadmap #18).
- Idea: roadmap #19's edits cover each of these. The description needs its
  continuation lines unindented, and a test that no description has a run of
  spaces.

## 2026-11-02: the first edit tools, built by hand
- Task: roadmap #19 (`docs/sessions/first-edit-tools.md`).
- Tool: `inspect` for reads of this repo's Java; the edit tools did not exist in
  this session (it uses the build from when it started).
- What happened: moves of existing code by hand: `Gate.apply` out of
  `RenameLocal.run` (extract_function), `Server`'s tool constants, handlers and
  argument helpers into `Tools` and `Args` (move_declaration), about 150 lines
  written out again. Correction to the entry on source roots: the runs of spaces
  in `rename_local`'s description came from writing the Java through a Python
  string, not from text block continuations.
- Cost: not measured (roadmap #18).
- Idea: extract_function (#23); a move of declarations between files is not on
  the roadmap; log it again when it recurs before adding it.

## 2026-11-02: imports, the first item built through the edit tools
- Task: roadmap #20 (`docs/sessions/imports-in-edits.md`).
- Tool: `inspect`, `create_file`, `replace_text`, `add_declaration`,
  `replace_declaration` for every change to Java; golden fixtures by shell.
- What happened:
  - Four refusals for a missing import (`Arrays`, `List`, `ONE_AT_A_TIME`
    declared after its use, and the signature change below), each answered with
    another tool call first: the gap this item closes, except the last.
  - Changing `Edits.write`'s signature and its caller: each alone was refused
    (the other did not compile). Composed as add the new overload
    (add_declaration), move the caller (replace_text), delete the old one
    (replace_declaration with empty content). Three calls where batch with check
    "end" would be one (roadmap #8).
  - `add_declaration`/`replace_declaration` refuse unparseable content without
    saying where; `create_file` names the lines. Cost one blind retry.
  - One `replace_text` `old` did not match text that `inspect` showed; a shorter
    `old` from the same lines matched. Cause not found.
- Cost: not measured (roadmap #18).
- Idea: batch (#8, next); line numbers in the members-do-not-parse refusal.
