# change_signature callers=catch: one try around consecutive calls

- **Item:** issue #478 (code review of 2026-11-13, signatures #4), group 2 of tracking issue #532
- **Date:** 2026-11-13
- **Branch:** fix-catch-consecutive-calls

## Plan

Reproduce with a golden case: two calls of the changed method in consecutive statements, and a
third after another statement. Then the one change: `ThrowsCallers.write` treats tries around
adjacent statements of one block as overlapping, so they merge. Sort the tries by start first, so
a run of three merges whatever order the calls were found in.

## Notes

- read: `inspect` with `pattern` and a line range of `ThrowsCallers`; the issue's sites held.
- plan: golden `change_signature_catch_adjacent` failed as the issue said ("Unreachable catch block
  for IOException").
- edit: one `replace_text` through `bin/jrefactor` (JSON with `@@` sections); `Comparator` imported
  by the tool.
- hand: the golden files, the docs.

## Measured

Not measured: the session-log hook is not loaded here. One tool edit to Java, none by hand.

## Assessment

Through the tools. The fix is in the merge rule, not in `TextEdit`'s ordering of inserts at one
offset, which the issue also suggests documenting; the merge removes the only known case where two
of this tool's inserts meet.

## Follow-ups

- `TextEdit.apply` refusing overlapping edits (the review's finding 9) is another issue.
