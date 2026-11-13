# The `;` after an enum's constants when its first member is added

- **Item:** roadmap #141 (order 134): increment 1 of `docs/sessions/enums-sketch.md`, a bug with
  a reproduction (section 7): a member added to an enum with constants only is written with no
  `;`, so the edit is refused as a syntax error
- **Date:** 2026-11-13
- **Branch:** enum-member-separator

## Plan

One helper that knows an enum's constants end with a `;` before its first member, asked by the
places that add a member at a type's end: `Declarations.plan` (which the stubs write through) and
`MemberMove.appendTo` (which `addTo` falls back to). Goldens first, each failing: `add_declaration`
on a multi-line enum, on a one-line enum, the field stub, and `type_switch_to_method` onto a
one-line enum that implements the interface (where `appendTo` also drops the constants). Then the
helper, added with `add_declaration`, and one `replace_text` per caller.

## Notes

- read: `Declarations.add`, `plan`, `MemberMove.appendTo`, `addTo`, `memberIndent`,
  `Placement.place` by `inspect` with `names`; `Stubs` by `pattern` (it writes through
  `Declarations.plan`, so it needs no change of its own).
- plan: seven goldens red before the code (five by a syntax error or the gate, two by layout):
  the four the sketch names, and `add_declaration_enum_trailing_comma`, `_separator_present`,
  `_no_constants` for the three other shapes of an enum body.
- refusal: the first batch was refused, "add_declaration needs file": the steps were written
  without `args`. Resent in the right shape; my error, the refusal said what was missing.
- bulk: `MemberMove.afterConstants` and `pastComments` by one `add_declaration` (55 lines), the two
  callers by `replace_text`, in one batch with check `end`; a second batch of two `replace_text`
  to call the helper once in `plan`.
- plan: the stub golden's expected text was mine and wrong (`public static`); the stub in the same
  package is `static Size HUGE;`. The golden was corrected, not the code.
- hand: nothing in `src/main` or `src/test/java` was edited by hand; goldens and docs are plain
  files.

## Measured

Not measured: the session-log hook is not loaded in this session. From the notes: 2 batches
(5 steps), 1 refused call, 0 hand edits of Java.

## Assessment

Through the tools. The plan held: one helper, two callers. Reading was by `inspect` with `names`
and `pattern`. What would have helped: `GoldenTest` cannot run one case, so each check ran all
553 (about 30 s); recorded as a follow-up.

## Follow-ups

- A way to run one golden case (`-Djrefactor.golden=<name>`), for a session iterating on one.
- A class body on one line with a member (`class C { int x; }`) still gets the new member after a
  line break inside the braces, not opened up, in `Declarations.plan` (by reading); only the enum case opens
  the body up.
