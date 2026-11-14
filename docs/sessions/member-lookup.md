# Member and type lookup by name, shared

- **Item:** issue #483 (code review, tracking issue #532 group 3).
- **Date:** 2026-11-13
- **Branch:** member-lookup

## Plan

Reproduce the refusal loop (two overloads written alike, a bare name) and the missing erased name
with golden cases; make `member` take erased names and hand back `methodLabel`; then extract the
name parse and the overload match from `Declarations.find` and `member` into one place, and point
the dotted type-path copies at `Declarations.typePath`.

## Notes

- red: `move_refused_overloads_written_alike` (suggested `Ex.raise(T)`), `move_overload_erased`
  ("declares no raise(Throwable)").
- green: `insert_statements` (the erased fallback) and two `replace_text`s (`methodLabel`).
- refactor: `extract_function` of `find`'s name parse (returns a record), `rename` of the record,
  widened by `replace_text`; `member`'s copy replaced by the call.
- refactor: `extract_function` of `member`'s overload match, `move` to `Declarations`; `find`
  rewritten to call it (`replace_statements`).
- refusal: `move` of `MemberMove.label`, a method reference in `EnumField`; swapped first.
- refactor: `rename` to `writtenLabel`; `typePath` used by `MoveType`, `MoveToPackage`, `Stubs`.
- hand: none in Java.

## Measured

Not measured (no session-log hook here). About 14 tool calls: 7 refactorings, 2 refusals, the
rest edits.

## Assessment

Through the tools. `MemberMove.member` keeps its field-or-method rules and its own wording.

## Follow-ups

- `MemberMove.typeNamed`, `FromFields`, `ExtractType`, `Edits`, `Fold` and `Opened.topLevel` still
  find a top-level type by name each their own way (classes only, or any kind); the review counted
  them, and roadmap #74 keeps separate resolvers.
