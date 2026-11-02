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
