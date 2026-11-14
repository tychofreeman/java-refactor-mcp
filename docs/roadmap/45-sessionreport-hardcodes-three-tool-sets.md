---
order: 38
item: 45
status: Done
---

## Item

`SessionReport` hardcodes three tool sets that copy `Tools.ALL`, so a new tool counts as "other"; it matches the text "refused:" that `Server` writes

## Status

Done

## Evidence

`Tools.Def` has a `Kind` (read, refactoring, edit, control) and the report counts by it; `Refusal.PREFIX` is the one refusal prefix (a flag logged by `SessionLog` would still match text, since the hook sees only the result). Also: quoted text and heredoc bodies are not redirects, and a shell write counts lines of Java only for a `.java` target (friction log, 2026-11-03)
