# A switch over an enum to a method in each constant: `type_switch_to_method`, method form

- **Item:** roadmap #152 (order 145): slice 3 of `docs/sessions/enums-sketch.md`, increment 6
- **Date:** 2026-11-13
- **Branch:** enum-switch-method (from enum-switch-field, #392, before it merged)

## Plan

`EnumSwitch` (increment 5) gains the method form: each case's body, the switch's value as `this`,
becomes `@Override public R name()` in its constant's body (written, or appended), and the enum
declares the method (abstract; the default's body; empty for a statement that leaves constants
out). The field form is kept for a switch expression of constants unless `as` says `method`.
Green first: `TypeSwitch.body` takes its indentation (`add_parameter`, then the literal replaced),
and `TypeSwitch.Case` becomes package-private, so both forms write bodies the same way. Red:
the six goldens the sketch names (`type_switch_enum_method`, `_method_default`, `_statement`
folded into `_method_default`, `_statement_partial`, `_existing_body`, `_refused_declared`) plus
`_method_shared_case`, `_as_method`, `_refused_as_field`, `_refused_inherited`; probes
`type_switch_enum_method_default` and `type_switch_enum_name_capture` (a nested class of the enum
with the name of a top-level class a case constructs: refused by `NameCheck`).

## Notes

- read: nothing new beyond increment 5's reads; `Edits.parses`, `Lines.indentOf`,
  `Declarations.step`, `MemberMove.appendTo` and `afterConstants` by `inspect` with `names`.
- plan: `appendTo`'s `;` after the last constant overlaps a body inserted after that constant, so
  the enum's own method is added in a second pass, on the text with the bodies, reparsed.
- bulk: `add_parameter` of `indent` to `TypeSwitch.body` (two calls pass the old literal); one
  batch of four `replace_text` (the literal, the visibility of `body` and `Case`, the doc).
- bulk: one batch of four: `create_file` with `overwrite` of `EnumSwitch` (503 lines, the field
  form moved into `field`, the method form in `method` and `inBody`, the case reading in `arms`),
  the hook passing `as`, `EnumField.checkName` package-private, `as` in `Tools`. Then one
  `replace_text` turned a qualified `MethodInvocation` into an import (added by the tool).
- hand: deleted the golden `type_switch_enum_refused_bare_static` by mistake in a shell command,
  restored from git.
- plan: every golden and probe passed on the first run.
- bulk: `Tools.TYPE_SWITCH_TO_METHOD`'s description by one `replace_text`.
- default (to reopen): `EnumField.checkName` serves the method form too, so a field named as the
  method is refused though a field and a method of one name could coexist.
- default (to reopen): a constant whose body's closing brace shares a line with its last member
  is refused, naming the fix, over opening the body up.

## Measured

Not measured: the session-log hook is not loaded. From the notes: 5 tool calls that wrote, 0
refused, 0 hand edits of Java.

## Assessment

Through the tools. Rewriting `EnumSwitch` whole by `create_file` was a large write (503 lines);
a `replace_declaration` of `run` with `add_declaration` of the new members would have sent about
as much, since most of the class changed. Reading was by `inspect`.

## Follow-ups

- Increment 7 (`yield` blocks) and 8 (nested enums).
- `RefactoringStressTest` on a real project over every switch on a project enum, as the sketch
  asks after increment 6: not run here (the brief keeps runs short on a shared machine).
