# ChangeSignature.run in parts (#528, item 5, first half)

- **Item:** issue #528, item 5 of its last comment: `ChangeSignature.run` (123 lines). The other
  half, `TypeParameters.toClass`, `toMethod` and `bound`, waits: another agent works on
  `TypeParameters` (#375).
- **Date:** 2026-11-15
- **Branch:** change-signature-run

## Plan

The five argument values every part reads become one record: `extract_function` of the five
lines that read them (several results give a record), `rename` of it to `Request`, `inline` of
the five locals. Then `extract_function` of each part, bottom first: the callers, the throws
clauses, the return type, the type checks.

## Notes

- extract_function: `request` (5 results as `RequestResult`); `rename` to `Request` and its
  local to `request`; `inline` of `returnType`, `added`, `removed`, `callers`, `stops` in one
  batch. The inline reported about 90 exceptional changes, one per use, each "the initializer
  may throw (request.added())": a record accessor of a local (friction log).
- extract_function, bottom first: `callerEdits` (8 parameters, returns the declarations count),
  `throwsEdits` (5), `returnTypeEdits` (6), `checkTypes` (1).
- An earlier try, `extract_function` of the whole throws block before the record, took 11
  parameters; `undo`.

## Measured

Not measured (no session-log hook). 5 `extract_function`, 1 `undo`, 2 `rename`, 5 `inline`,
1 `replace_text` (the record's doc); no Java by hand.

## Assessment

Through refactorings. `run` is 60 lines (from 123), most of them its preconditions and the
refusal's text. `callerEdits` still takes 8 parameters: `family`, `change` and `decls` travel
together and could be one value; not done here.

## Follow-ups

- `TypeParameters.toClass`, `toMethod`, `bound` after #375.
