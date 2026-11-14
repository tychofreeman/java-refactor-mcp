---
order: 168
item: 175
status: Done
---

## Item

`rename` errored with a JDT NullPointerException when a file its meaning check re-parses imports a single static method of a project type outside the parse (issue #411)

## Status

Done 2026-11-13 (`Jdt.staticImported`: `parseAll` parses the files that declare a type its files import a single static member of with them, not handed to the caller. JDT looks up such an import's binding only when asked, after the batch, and needs every member of the imported type resolved, which it does only for a type of the batch; for one read from the source path its scope is gone)

## Evidence

The Commons Lang stress run, `docs/sessions/stress-new-options.md`; `docs/sessions/fix-rename-static-import.md`
