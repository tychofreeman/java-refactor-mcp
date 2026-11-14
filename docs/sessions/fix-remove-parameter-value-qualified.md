# remove_parameter value of another class's constant

- **Item:** issue #464 (code review of 2026-11-13), roadmap #216 (211 until it collided with another row)
- **Date:** 2026-11-13
- **Branch:** fix-remove-parameter-value-qualified

## Plan

`readOf` writes a static field's simple name unless a local hides it, so `Config.OUT` became
`OUT`, which can bind to the method's class's own `OUT`. Write the bare name only when it finds
the field from the method's class (first of the name up the superclass chain), else
`Type.NAME`. One method replaced, one helper added.

## Notes

- read: `inspect` of `Signatures.java` ranges (same session as #463), `Extract.typeName` by `names`.
- plan: probe `remove_parameter_value_other_constant` first; it printed 2 where the program printed 3.
- `replace_declaration` of `Signatures.readOf` declared a stub for `firstNamed`; `add_declaration`
  replaced it. Both through `bin/jrefactor`.
- golden `remove_parameter_value_other_constant` pins `Math.min(n, Config.OUT) + OUT`.

## Measured

Not measured: the session-log hook is not loaded here. Two tool edits to Java, none by hand.

## Assessment

Through the tools; the stub-then-replace order worked as the instructions say.

## Follow-ups

- `fieldNamed`'s unqualified walk and `firstNamed` walk the same chain; one could return the
  binding for both.
