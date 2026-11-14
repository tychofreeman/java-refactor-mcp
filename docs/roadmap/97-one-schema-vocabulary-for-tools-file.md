---
order: 90
item: 97
status: Done
---

## Item

One schema vocabulary for `Tools`: `file`, `line`, `name`, `column` and the other shared arguments defined once and named by each `Def`, instead of hand-written JSON per tool

## Status

Done (2026-11-05), local session: `Arg` (`FILE`, `IMPORTS`, `string`, `integer`, `bool`, `oneOf`, `strings`, `json`, `required`, `described`); each `Def` lists its `Arg`s, and its schema, `required` and argument names are built from them. Every schema is the same as before (`ToolSchemasTest`, opt-in, diffed before and after); `Tools.java` 1,405 to 1,249 lines; record in `docs/sessions/schema-vocabulary.md`

## Evidence

Same review: `Tools.java` is 1,391 lines for 36 tools, with 114 hand-written string fields. Fits with splitting `Tools` (#32). No behavior change; `ToolsTest` and the goldens are the check
