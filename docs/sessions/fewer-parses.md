# Fewer repeated parses (#529)

- **Item:** issue #529, from the code review of 2026-11-13 (group 5 of #532): `ChangeType` parses
  the field's files twice, `ParameterValue.sameTargets` parses the calling file once per call,
  `TypeParameters.ordinal` walks the file once per use, and `MoveMember.withAlong` runs a whole
  `move` per private callee.
- **Date:** 2026-11-14
- **Branch:** fewer-parses

## Plan

Measure first: `GateCostTest.timesACall` counts the gate's parses only, so count every binding
parse (`Jdt.parser`) in the session and print it beside them. Then, each a change that keeps
every result and refusal:

- `ChangeType.run`: one `Holding` for `checkUses` and `CallTargets`.
- `sameTargets`: split the probe (the value written into the call, parsed to see what its calls
  resolve to) from the per-call checks; probe every call of a file in one parse, before the
  per-call checks run, and keep each call's refusal for its turn, so the order of refusals holds.
- `ordinal`/`byOrdinal`: one walk per file, an index both ways.
- `withAlong`: measure; batch only if it matters (the review: "acceptable for now").

## Notes

- read: the issue, `ChangeType`, `ParameterValue.sameTargets` and `perCall`, `Introduce`'s
  `as=parameter` path, `TypeParameters.callTypes`/`ordinal`/`byOrdinal`, `MoveMember.withAlong`.
- plan: the fixture for the measurements is generated (`p/Pricing.java`, 20 callers with 50
  calls of `price` and of `echo` each, and `total` with three private callees); `change_type` is
  measured on Commons Lang (`StrSubstitutor.substitutionDepth` to `long`).
- refusal: `insert_statements` with `position=before` ("does not take position"): it is `where`.
- refusal: `batch` with `content_file=-`: a batch is a JSON object, `bin/jrefactor batch -`.
- refusal: `extract_function` 959-1020 "cut the statement at line 959" (the cut was the closing
  brace at 1020; logged).
- read: the "calls another method" refusal had no golden; added
  `extract_variable_parameter_call_other_method` (two calls in one file), passing before and after.
- bulk: the rebase onto main conflicted in the friction log only (both entries kept).

## Measured

Session: not measured (no session-log hook here); every Java change went through `bin/jrefactor`
(about 9 calls: 5 batches, `extract_function` twice, `add_declaration`, `insert_statements`).

Call costs, `GateCostTest.timesACall` (binding parses = every `Jdt.parse` and `parseAll` batch;
times on a shared 2-core machine, noisy):

| Call | Before | After |
|---|---|---|
| `extract_variable` `as=parameter` `o.qty()`, 1,000 calls in 20 files | 1,045 parses, 10.5 s | 65 parses, 3.1 s |
| `add_parameter` `type_parameter` on `echo`, 1,000 calls | 8 parses, 1.2 s | 8 parses, 0.9 s |
| `change_type` `NumericEntityEscaper.below` to `long` (Commons Lang) | 7 parses | 6 parses |
| `move` `Pricing.total` with three private callees (22 files) | 4 gate counts, 37 parses, 1.6 s | unchanged |

## Assessment

Through the tools; the plan held. The probe batching keeps the order of refusals by computing
them before the per-call checks and throwing each in its call's turn. `withAlong` stays: the
review called it acceptable, and four gates on a small project cost 1.6 s.

## Follow-ups

- `withAlong` in one parse, if a project with many callees shows the cost (#529's second finding).
- `extract_function`'s cut-line refusal (friction log).
