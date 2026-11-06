# Stubs: a static field, and a stub replaced across erasure

- **Item:** roadmap #107, a parity bug (gorefactor's `docs/parity.md` section 1) from the friction
  log: `SessionLog.MAX_RESULT` read by a test first (2026-11-03), and `allPure(List<?>)` refused
  beside its stub `allPure(List)` (2026-11-02).
- **Date:** 2026-11-06
- **Branch:** stubs-fields-erasure

## Plan

1. A read of `T.NAME`, where `T` is the project's class (or a class that does not exist yet,
   named with a capital) and `NAME` is not declared, declares `static <type> NAME; // not
   implemented` in `T`, the type from the use as for methods. `add_declaration` of a field of that
   name replaces it, as a method stub is replaced.
2. `add_declaration` replaces a method stub whose parameter types erase to the same types
   (`List` and `List<?>`), not only one written the same.

Derivation: goldens first; `Stubs.Round.find` gets `UndefinedField` and a qualified receiver of
`UndefinedName`, and a `field` beside `method` (`add_declaration`); `Declarations.stubFor` takes
any member, compares erased parameter types, and knows a field stub by its marker.

## Notes

- read: `Stubs.Round.find` and `method` (where problems become stubs), `Declarations.plan` and
  `stubFor` (where a stub is replaced).
- Red as planned; then three fixes the goldens showed: "cannot be resolved to a variable" is
  `IProblem.UnresolvedVariable`, not `UndefinedName` (found with `javap`); `Declarations.end` is
  the last character, not one past; a new class stub is `public class` (the convention).
- plan: `stubs_refused_leaves_no_class` now fails one step later (`Settings.MAX` is stubbed as
  `Object`, so `+` is refused); it still shows that a refused edit leaves no class. Typing an
  arithmetic operand from the other operand would make it pass: not done, no case asks yet.
- The #106 shift took a contract `old` 2 spaces too deep, and said so.

## Measured

Through jrefactor: 5 batches (the stub case and field, the replacement, two fixes, the docs);
no refusals; by hand the goldens' fixtures (resources), two expected outputs by `sed`, this
record.

## Assessment

Through the tools. The field stub's marker is a comment, a choice made here and recorded in the
session notes; the alternative, a value, could not fail a test the way a method stub does.

## Follow-ups

- An arithmetic operand typed from the other operand (`Settings.MAX + 1` as `int`).
