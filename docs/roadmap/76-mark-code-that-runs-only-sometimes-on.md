---
order: 69
item: 76
status: Done
---

## Item

Mark code that runs only sometimes on `Effect` (`conditional`), replacing the parent walks in `ExtractVariable.checkPlace` (loop conditions, `&&` and `||`, conditionals) and `InlineFunction.deferral`

## Status

Done (2026-11-05), local session: `Effects.later` and `Effects.sometimes` (a query on `Effects`, not a field on `Effect`), used by both; they add the branches of statements (`if`, loops, `switch`, `catch`), which fixed `inline_function` moving an argument with an effect into an `if` or a loop (found by a probe, applied and type-checked); record in `docs/sessions/effect-conditional.md`

## Evidence

Follow-up of #72 (`docs/sessions/one-effects-analysis.md`). Not a pure refactoring after all: it fixed two behavior changes and allows the condition of a `?:` and the left of `&&` in `extract_variable`
