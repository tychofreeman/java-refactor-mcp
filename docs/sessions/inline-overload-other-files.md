# inline of an overload: calls in other files

- **Item:** issue #612 (friction log), roadmap item 501
- **Date:** 2026-11-15
- **Branch:** inline-overload-other-files

## Plan

Reproduce with a golden of two overloads called from a second file. The issue guessed bindings
compared across parses; read how `InlineFunction.run` gathers files and matches calls, and
fix whichever step loses them.

## Notes

- read: `inspect` of `InlineFunction` (outline) and `Holding.of`; `run`'s body with sed.
- plan: two goldens, red: "has no calls in the project", and a gate refusal for calls left.
- cause: `Holding.of(project, member, file)` with `member` = `twice(int)`: the text filter kept
  only the declaring file. Calls were already matched by `Bindings.same` (by key).
- `replace_text` of the one line, with a comment (the first try, with the text inline, broke on
  an apostrophe in the shell; sent with `new_file=-`).
- hand: goldens' expected output corrected to the tool's parentheses (`(n * 2) + (1 * 2)`),
  as for other binary contexts; docs.

## Measured

Not measured (the session-log hook is not loaded here). From the notes: 1 tool edit to Java,
0 by hand.

## Assessment

Through the tools; a one-line fix once read. The issue's guess (bindings across parses) was
wrong: the call match was by key already.

## Follow-ups

- `MoveMember.java` line 328 calls `Holding.of(project, member, file, targetFile)` with the
  member as written (`q.member()`, parameter types included): `move` of `Type.method(int)` likely
  misses calls in other files the same way. Not changed here (one issue per PR); worth an issue.
