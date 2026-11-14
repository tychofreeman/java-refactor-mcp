# Holding.of for every "files that hold a name" walk

- **Item:** issue #502 (code review: core #9, moves #9, extract-inline #16)
- **Date:** 2026-11-14
- **Branch:** holding-names

## Plan

One helper for "the project's files whose text holds a name, plus some files, parsed together":
`Holding.of` with several extra files (`Path...`, normalized), and `Holding.where` with a
predicate on path and text for the copies that test something else (`ExtractType`'s several
names, `MethodFamily`'s lambda pass). Port each copy with `replace_statements`; make the `null`
callers pass nothing. `inspect` `names`: read and `Jdt.parseSyntax` each file holding the simple
name. No behavior change, so the goldens are the check; a unit test for the helper.

## Notes

- read: `inspect` with `pattern` for `Holding.of` and `javaFiles()`, line ranges of each copy.
- plan: the copies' `toAbsolutePath().normalize()` is a no-op: `SourceProject` normalizes its
  roots, so `javaFiles()` is normalized already; `Holding.of` normalizes `also` once anyway.
- plan: the copies that kept the caller's `text` for its own file (`MoveMember`,
  `EncapsulateField`, `InlineFunction`) kept the file's own text: `Jdt.parseAll` reads disk, so a
  different text could not match the unit. No override map.
- refusal: `change_type` `type=Path...` does not parse (friction log); `replace_declaration`.
- bulk: one batch of 7 (`Holding`, the `null` callers), one of 9 (the copies, `inspect`), one of
  1 (`ThrowsCallers.index`, found on a second `pattern` search after rebasing).

## Measured

Not measured: the session-log hook is not loaded here (the CLI log, `JREFACTOR_LOG`, is).
From the notes: 3 batches and 1 `create_file` of Java, no Java by hand.

## Assessment

Through the tools. `MethodFamily` and `ThrowsCallers` read each file once more than before
when they reach their second pass (only for an interface method, and for a new checked
exception): `Holding.where` reads every file to test it, where they reused the texts read first.

## Follow-ups

- `inspect` with `file` still parses with bindings (`Inspect.file`); it reads one file, so it was
  left.