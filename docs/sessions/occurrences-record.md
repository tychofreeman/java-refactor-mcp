# A record for parameter occurrences, comma lists in Args (#530, part 2)

- **Item:** issue #530 (code review, group 5 of #532), the findings part 1 (#596) left:
  `ParameterValue.occurrences`' `int[4]`, its `Swap` record, `ChangeSignature.names`.
- **Date:** 2026-11-14
- **Branch:** occurrences-record

## Plan

1. `ParameterValue.occurrences` returns `Occurrence(offset, length, parameter, receiver)` records:
   no tool turns an array into a record, so one batch of edits.
2. `Swap`: already gone on `main` (`substituted` builds `TextEdit`s); nothing to do.
3. `ChangeSignature.names` parses the comma lists: `move` to `Args`, `rename` to `commaSeparated`.
   Making the arguments arrays changes the contract: the smallest option (keep strings) is the
   default, recorded in `docs/design.md` Decisions with the others.

## Notes

- read: `inspect` with `pattern` for the three sites, then the two method ranges.
- hand: the `Occurrence` record, as one batch of 7 edits (friction-log entry).
- move: `ChangeSignature.names` to `Args` (3 calls rewritten); rename to `commaSeparated`.
- plan: `Swap` was already replaced by `TextEdit` on `main` before this branch.

## Measured

Not measured (no session-log hook in this container). 1 move, 1 rename, 1 batch of 7 edits.

## Assessment

Through the tools; the record conversion had no refactoring and was done as edits. No behavior
change; goldens and probes pass unchanged.

## Follow-ups

- An array-to-record refactoring, if more such arrays turn up (`InlineFunction`'s and `Fold`'s
  parallel arrays in #528 are a different shape: several arrays, one per field).
