---
order: 227
item: 235
status: Done
---

## Item

`Server.answerAll` caught `Refusal` and `Exception` only: a `StackOverflowError` from deep recursion, or an `OutOfMemoryError`, left the answer to the MCP SDK (issue #490, code review)

## Status

Done 2026-11-13 (`answerAll` catches `Error` too and reports it as an engine error, rethrowing other `VirtualMachineError`s; `ServerTest.aStackOverflowIsAnEngineErrorAndTheServerAnswersAfter`. Default to reopen in Decisions)

## Evidence

Issue #490; `docs/sessions/fix-server-catch-error.md`
