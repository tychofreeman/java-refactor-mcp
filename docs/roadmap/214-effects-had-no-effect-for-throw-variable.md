---
order: 206
item: 214
status: Done
---

## Item

`Effects` had no effect for `throw <variable>` or `assert`, and `Effects.leaves` ignored `throw`: `count++` moved above `if (n == 0) throw err;` (or below it) was neither refused nor reported, and a division moved above such a guard changed which exception came (issue #474, code review)

## Status

Done 2026-11-13 (`Effects`: `MAY_THROW` for a `throw`, an `assert`, an integer `/=` or `%=` and an unboxing; `Effects.leaves` true for a throw no try inside catches, by `Effects.caught`, when `throwing` (`LoopToStream` passes false: a lambda may throw). Probes `move_statement_past_throw_guard{,_down}`, `move_statement_division_past_throw_guard`; golden `move_statement_refused_throw_guard`; `EffectsTest`. Every golden case and probe kept its output. Default to reopen in Decisions: refusing as an early exit over reporting)

## Evidence

Issue #474; `docs/sessions/fix-effects-throw.md`
