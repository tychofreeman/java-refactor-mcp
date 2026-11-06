# move_member into another file's class

- **Item:** roadmap #109, part 1 (methods; static fields are part 2): friction triage, two moves
  into another file's class done by hand (2026-11-03).
- **Date:** 2026-11-06
- **Branch:** move-member-other-file

## Plan

`move_member`'s `to` may name a top-level class in another file (`Tax`, or `com.example.shop.Tax`
when several files declare one): the method goes to the end of that class, calls become
`Tax.withTax(...)` as within a file, imports follow in every file (the gate's import fix), and
across packages the moved method becomes public. What the moved body uses that the target cannot
reach (a package-private field of the source, from another package) is a compile error the gate
refuses, naming it.

Derivation: goldens first (same package; another package); in `MoveMember.run`, the target found
in the project when the file has none (a helper `targetFile`), parsed with the others; the
appended text goes to the target's file; `movedEdits` makes it public across packages.

## Notes

- read: `MoveMember.run` and `movedEdits`, `MemberMove.gate`, `appendTo`, `typeNamed`.
- One batch of eight steps; then one fix the golden showed: `public` and `static` inserted at the
  same offset came out `static public`; merged into one insertion (`replace_statements`).

## Measured

Through jrefactor: 2 edit calls and the docs batch; no refusals; by hand the goldens (resources)
and this record.

## Assessment

Through the tools, as planned.

## Follow-ups

- #109 part 2: static fields and constants.
