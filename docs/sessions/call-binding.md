# Calls.binding: one call-to-binding mapping

- **Item:** issue #503 (code review 2026-11-13, group 3 of #532): the method a call node runs
  was worked out in five places, and only `MethodFamily` applied `Signatures.constructed`.
- **Date:** 2026-11-14
- **Branch:** call-binding

## Plan

Reproduce first: `add_parameter` by the line of `new Base(1) {...}` (`ByLine`), and
`change_signature` `add_throws` on `Base(int)` with that construction calling it
(`ThrowsCallers`). Then `create_file` a `Calls` class, `move` `CallTargets.binding` (already
the full switch) into it, give it `constructed` for constructions, and point each site at it:
`TypeParameters.callBinding` a delegate, then `inline`; the other sites `replace_text`.

## Notes

- read: issue body; the five sites by line ranges.
- refusal (reproduced): `add_parameter` refused "Use..(int) is not written in Use.java (a default
  constructor)"; `change_signature` refused "Unhandled exception type IOException" in `Use.java`.
- plan: `change_type` of the constructor's parameter already passed (CallTargets fell back to
  something that compared equal); kept as a pin.
- The batch (6 steps) passed at the end check first time.
- Through the tools: 1 `create_file`, 1 `move`, 1 `inline` (refactorings); 2
  `replace_declaration`, 3 `replace_text` (edits). Nothing by hand in Java.

## Measured

Not measured: the session-log hook is not loaded here. Counts are from the notes.

## Assessment

Through the tools. The two failures reproduced before the change and pass after; every other
golden kept its output. `CallTargets` now compares an anonymous class's construction by the
superclass constructor it runs, which is what a change of that constructor needs.

## Follow-ups

- `ByLine` has no hit for an enum constant's line.
- `SafeDelete` and `Signatures.addComponent` still compare the raw constructor binding.
