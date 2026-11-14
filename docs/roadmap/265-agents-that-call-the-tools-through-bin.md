---
order: 255
item: 265
status: Done
---

## Item

Agents that call the tools through `bin/jrefactor` in a session without the Claude Code hooks left no session log, so their tool use could not be measured

## Status

Done 2026-11-14: `JREFACTOR_LOG=<file>` makes each `Cli` run append one line in `SessionLog.entry`'s format, a Bash call of `bin/jrefactor` with JSON arguments written out as their objects, which `bin/session-report <file>` reads unchanged (`Cli.logLine`, `CliTest`); a failure to log changes nothing

## Evidence

The owner, 2026-11-14; `docs/sessions/cli-log.md`
