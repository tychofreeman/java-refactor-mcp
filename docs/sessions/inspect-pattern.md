# inspect with pattern

- **Item:** finding a line of Java had no tool route: `inspect` reads an outline or a
  range, and the read guard refuses `grep -n` of Java, so a search took an outline and
  a range, or `bin/jrefactor inspect | grep` (the same cost as grep). The user asked for
  `pattern` and `names` on 2026-11-05; this is `pattern`, `names` follows.
- **Date:** 2026-11-05
- **Branch:** inspect-pattern

## Decisions (this session)

- **`pattern`, a Java regular expression,** with `file` (its matching lines) or without
  (every Java file of the project, in path order): the lines numbered as `inspect`
  numbers them, the width that of the largest number shown, under a header per file
  with the count. At most 100 lines, then how many more. No match is a result, not a
  refusal. Refused: an invalid expression (saying to escape), and a pattern with a line
  range.
- **Java's first:** gorefactor's `inspect` has no pattern; the contract says so, so
  the shared tool can take it later.
- The read guard's refusal now names `pattern`, and CLAUDE.md says to use it to find a
  line or a use.

## Notes

- Goldens red first (an unknown argument); my first expected output forgot the code's
  own indentation after the two spaces.
- Checked on a lookup the guard refused today: `pattern='overloaded\(mb\)'` gives line
  410 of `Fold.java`.
- `hand:` none.

## Measured

All Java through jrefactor (`add_declaration`, `replace_declaration`,
`replace_text`); goldens by `create_file`.

## Follow-ups

- `names` (next PR); `pattern` in gorefactor's `inspect`.
