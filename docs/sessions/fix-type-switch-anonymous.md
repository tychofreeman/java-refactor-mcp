# type_switch_to_method: the pattern variable inside an anonymous class

- **Item:** issue #551 (found while fixing #468; roadmap row 267, order 257)
- **Date:** 2026-11-14
- **Branch:** fix-type-switch-anonymous

## Plan

Probe first: a case body whose anonymous `Runnable` prints `c.toString()` and declares its own
`toString()`. Then refuse, in `TypeSwitch.asReceiver` (which the enum forms share), a use of the
pattern variable inside an anonymous or local class of the body, and name the fix: a local before
the class. Pin that the fix then applies.

## Notes

- read: the probe printed `anon` where it printed `circle`; the golden applied.
- plan: one `batch` (`insert_statements` of the check, `add_declaration` of `inClass`), then a
  `replace_text` to name the variable's own type (an enum case has no case type).
- hand: probe and golden resource files.

## Measured

Not measured: the session-log hook is not loaded here. Every Java change through `bin/jrefactor`.

## Assessment

Through the tools; the plan held. Refusing over writing `Circle.this`: the smaller change, and
the refusal's fix gives the same code through the tool.

## Follow-ups

None.
