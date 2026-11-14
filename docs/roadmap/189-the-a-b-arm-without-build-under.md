---
order: 182
item: 189
status: Done
---

## Item

The A/B arm without `build` (issue #376's "mcp today"): under `JREFACTOR_NO_BUILD=1` the server publishes `insert_statements`, `replace_statements` and `replace_expression` as they were before #149, and refuses the create aliases

## Status

Done 2026-11-13 (`Tools.published` and `Tools.named(name, session)`, the typed `Def`s in `Tools.TYPED`; `eval-tasks/jrefactor-ab-server-no-build`)

## Evidence

Issue #376; the user's choice of 2026-11-13, a server flag over a jar from before #149; `docs/sessions/no-build-arm.md`
