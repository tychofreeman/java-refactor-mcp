# Blocks with `yield` in a switch over an enum

- **Item:** roadmap #153 (order 146): slice 3 of `docs/sessions/enums-sketch.md`, increment 7
- **Date:** 2026-11-13
- **Branch:** enum-switch-yield (from enum-switch-method, #397, before it merged)

## Plan

A block case of a switch expression over an enum becomes the method's body (the method form
already copies a block's lines), each `yield` targeting the converted switch rewritten as `return`.
Where: `TypeSwitch.asReceiver` already edits the body's text (the receiver), so the yields join its
edits; `usesOnly` refuses a jump that leaves the body, so a yield to the converted switch is let
through there by one predicate (`ownYield`: the yield's innermost switch expression is the case's
parent). `EnumSwitch.arms` takes a block in an expression as it does in a statement. Red first:
goldens `type_switch_enum_yield` (a yield inside an `if`), `_refused_yield_outer` (the converted
switch a statement inside an outer switch expression, its case yielding to it); probe
`type_switch_enum_yield` (a yield inside a nested old-style switch statement, whose target is the
converted switch, and a nested switch expression's own yield), checked with `javac` first that
the probe's input compiles.

## Notes

- read: `TypeSwitch.usesOnly`, `leaves`, `asReceiver` (read in increment 5).
- bulk: one batch of six: `usesOnly`'s jump test, the yield edits in `asReceiver`,
  `add_declaration` of `ownYield`, the block in `EnumSwitch.arms`, `constant` taking any node (a
  block is no constant). Checked at the end; compiled.
- plan: every golden and probe passed on the first run; `_refused_yield_outer` pins the existing
  refusal.
- bulk: the tool description by one `replace_text`.
- hand: nothing in Java.

## Measured

Not measured: the session-log hook is not loaded. From the notes: 2 tool calls that wrote, 0
refused, 0 hand edits of Java.

## Assessment

Through the tools; the plan held, the change small because increment 6 had the shape for it.

## Follow-ups

- The type-pattern form (`TypeSwitch.switchCases`) still refuses a block in a switch expression;
  `ownYield` would serve it as well.
