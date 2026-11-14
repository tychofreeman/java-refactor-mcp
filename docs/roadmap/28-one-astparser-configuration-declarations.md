---
order: 21
item: 28
status: Done
---

## Item

One ASTParser configuration: `Declarations.members` sets its own options and no environment; `Jdt` should own every parser

## Status

Done

## Evidence

Code review 2026-11-03. `Jdt` owns every parser now (`Jdt.parseMembers`, `options()`); record `docs/sessions/one-parser-config.md`
