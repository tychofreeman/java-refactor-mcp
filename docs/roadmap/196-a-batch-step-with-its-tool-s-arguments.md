---
order: 189
item: 196
status: Done
---

## Item

A `batch` step with its tool's arguments beside `tool` rather than under `args` was refused by the step's tool ("replace_text needs file"), which does not say where they go (four friction entries, 2026-11-10 and 2026-11-13)

## Status

Done 2026-11-13 replaced by #198, which reads them; `Batch.argumentsUnderArgs`, before any step runs: a key beside `tool` that the tool takes, by its name, an old one or a retired one, is refused naming the keys and showing the first such step with them under `args`, then the other such steps by number; other keys stay ignored. Default to reopen: refuse over reading them as arguments, in Decisions)

## Evidence

`docs/friction-log.md`; `docs/sessions/batch-args-beside-tool.md`
