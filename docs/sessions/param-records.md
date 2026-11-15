# InlineFunction's and Fold's parameter facts (#528, item 1)

- **Item:** issue #528 (code review, group 5 of #532), item 1 of its last comment:
  `InlineFunction.replacementFor` (17 parameters) and `Fold.run`'s parallel arrays.
- **Date:** 2026-11-15
- **Branch:** param-records

## Plan

`replacementFor` takes what `run` knows of the inlined method one value at a time: a parameter
object. Make the group adjacent (`move_parameter` of `siteText` last), widen `ParamUse` from
private (a top-level record cannot name a private member type), `extract_type` of the 14 into
`InlinedMethod`, then build it once before the loops rather than per call. In `Fold.run`, the use
count, first end and last start are computed in one loop and `stable` from them:
`extract_function` of each loop (the first has five results, so a record).

## Notes

- refusal: `move_parameter` of `siteText` before `value`: the arguments `m.getBody()` and
  `instance ? thisUses : List.of()` "may both have side effects". Moved it last instead (past three
  plain locals); `retry` of the one step.
- edit: `extract_type` wrote `new InlinedMethod(...)` into the call inside the two loops;
  `replace_statements` of the call and `insert_statements` of the local before the loops (no tool
  hoists, as in moved-type-context).
- edit: `replace_text` of the record's `public` and its doc; `rename` of the parameter to `inlined`
  (`method` is the binding in `run`).
- extract_function: `Fold.stable`, then `Fold.paramUses` (results as `ParamUsesResult`); `rename` to
  `ParamUses`, its local to `facts`; `inline` of `firstEnd` and `lastStart` (reported as possibly
  throwing: `facts.firstEnd()` on a local the tool cannot prove non-null).

## Measured

Not measured (no session-log hook). 1 `move_parameter`, 1 `extract_type`, 2 `extract_function`,
3 `rename`, 2 `inline`, 6 edits (`replace_text`, `replace_statements`, `insert_statements`); no
Java by hand.

## Assessment

Through the tools. `replacementFor` 17 -> 4 parameters; `Fold.run` 35 lines shorter. The review's
"record per parameter" (an array of records in place of parallel arrays) was not reached: no tool
or short sequence turns parallel arrays into one array of records (friction log).

## Follow-ups

- `InlinedMethod` still holds `useCount[]` and `subtypeSafe[]`, and `ParamUses` holds `uses[]`,
  `firstEnd[]`, `lastStart[]`: arrays of records would need a transform no tool offers.
