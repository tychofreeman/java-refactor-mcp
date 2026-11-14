# A new accessor or folded method that overrides (issue #466)

- **Item:** code-review issue #466: `encapsulate_field` and `inline` with `field` could add a
  method that overrides an inherited one, or that a subclass overrides, and the gate passed it.
- **Date:** 2026-11-13
- **Branch:** fix-new-override

## Plan

Green first: move `FunctionToMethod`'s `clash` and `below` to a new `Overrides` (`create_file`,
`move` twice), add `Overrides.of` returning the clash and the subtype that declares it, and make
`refuseOverriding` call it with its own wording; run the `function_to_method` cases. Red: probes
for an inherited getter, a subclass's getter, an inherited and a subclass's folded method; goldens
for two refusal texts. Green: a `refuseOverride` in `EncapsulateField` (getter and setter of an
instance field) and in `InlineClass` (each folded instance method), each one call before the
edits.

## Notes

- read: `inspect` of `FunctionToMethod` lines 150-263, `EncapsulateField` outline and `run`,
  `InlineClass` lines 1-234.
- `create_file` of `Overrides`, `move` of `FunctionToMethod.clash` and `.below` (each widened
  to package access), a `batch` of two `add_declaration`s, `replace_declaration` of
  `refuseOverriding`; function_to_method cases green.
- hand: `sed` made `Overrides.clash` and `below` private again after the move (no tool narrows
  visibility; logged).
- plan: the four probes and two goldens failed as the issue said (`base` then `person`, and so on).
- `batch` of `add_declaration` and `replace_text` in each of `EncapsulateField` and `InlineClass`.
- refusal: a `replace_text` whose `new` held `\x27` (my shell quoting) did not parse; resent.

## Measured

Not measured: the session-log hook is not loaded here.

## Assessment

Through the tools except the visibility narrowing. The plan held. The supertype walk in
`Overrides.of` is now transitive over interfaces too (a superinterface's superinterface), a little
wider than `FunctionToMethod`'s was.

## Follow-ups

- Static accessors and folded static methods are not checked (a static can hide a supertype's
  static, which changes what `Person.getX()` names): reopen if a case shows it.
- `move` could keep a moved private member private when its only callers move with it, or the
  class it lands in is the only user.
