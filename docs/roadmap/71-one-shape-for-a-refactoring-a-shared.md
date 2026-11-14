---
order: 64
item: 71
status: Done
---

## Item

One shape for a refactoring: a shared target resolver (file, line, name, column to a type, member, statement or expression, with one refusal wording) and preconditions as values a tool lists, not `if`/`throw` in each `run`, so a check can refuse or report

## Status

Done for twelve tools (2026-11-04), local session: `Opened`, `Qualified`, `Precondition`; `Rename`, `MoveType`, `Extract`, `Declarations` and the signature tools keep their own resolvers (follow-up); record in `docs/sessions/refactoring-shape.md`

## Evidence

Review of the abstractions, 2026-11-04 (the user agreed to put it ahead of #73): 14 tool classes repeat the parse-and-locate preamble (13 copy `project.javaFile(rel)` then `Jdt.parse`), resolve targets their own way (`MemberMove.typeNamed`, `ExtractVariable.find`, `InlineVariable.variableAt`, `Rename.Query`), and refuse inline (22 refusals each in `MoveMember` and `InlineFunction`). A cross-cutting flag has no one place to attach. No behavior change; the goldens are the check
