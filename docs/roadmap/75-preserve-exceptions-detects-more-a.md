---
order: 68
item: 75
status: Done
---

## Item

`preserve_exceptions` detects more: a dereference moved past a guard, code moved into or out of a `try`, an `inline_function` argument that may throw and now runs later in the body, an `extract_variable` expression that may throw moved before an earlier state change in its statement

## Status

Done (2026-11-05), local session: `inline_function` arguments used only sometimes, `inline_variable` initializers moved into a guard, loop, lambda or `try` (`docs/sessions/exceptional-more.md`); `move_statement` past a guard (a statement with a side effect is refused, one that may throw reported) and `extract_variable` out of a try's resources (`docs/sessions/move-statement-guard.md`). An argument that may throw and now runs after a change in the body, and an extracted expression moved before an earlier change, were already refused by the normal-flow rules (they read state)

## Evidence

Follow-ups of #73 (`docs/sessions/preserve-exceptions.md`); the user's policy asks for the syntactically clear cases (Decisions, 2026-11-04)
