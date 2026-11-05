# inspect with names

- **Item:** the second of the two `inspect` options the user asked for on
  2026-11-05 (after `pattern`, #202): read a declaration by name, wherever it is, as
  gorefactor's `inspect` does.
- **Date:** 2026-11-05
- **Branch:** inspect-names

## Plan

Make the change easy first: extract `Inspect.file`'s numbered, elided loop as
`outline` and give it a line range (`extract_method`, two `add_parameter`s, then the
loop's bounds), so a type can be shown as the file view shows it. Then `names`.

## Decisions (this session)

- **gorefactor's argument and spelling:** `names`, alone, found anywhere in the
  project (`Holding` by the type's simple name). `"Type"` and `"Outer.Inner"` show a
  type with its bodies elided; `"Type.member"` each overload of a method, a field or
  an enum constant in full, Javadoc included (a nested type, again elided). A dotted
  name is tried as Type.member first, so `Outer.Inner` reads the nested type as a
  member of `Outer`.
- Refused: no such type, no such member (pointing to the type's outline), and
  `names` with `file`, `pattern` or a range. The guard's refusal and CLAUDE.md name it.

## Notes

- The refactoring kept every golden green before `names` was written.
- `refusal:` my batch's step for the `Def` used the text before Spotless wrapped it
  (after #202); resent from `inspect`.
- The write guard refused a `printf` of Java into a variable path (with #201's new
  message), rightly; the nested-type case was written as a golden with `create_file`.
- `names` read `Inspect.bodies` for this work, and `Fold.overloaded` as a check.
- `hand:` none.

## Measured

All Java through jrefactor (`extract_method`, `add_parameter`, `add_declaration`,
`replace_text`); goldens by `create_file`.

## Follow-ups

- gorefactor's `targets` and `uses`; `pattern` in gorefactor.
