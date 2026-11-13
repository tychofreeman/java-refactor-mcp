# A switch over an enum to a field: `type_switch_to_method`, field form

- **Item:** roadmap #151 (order 144): slice 3 of `docs/sessions/enums-sketch.md`, increment 5
- **Date:** 2026-11-13
- **Branch:** enum-switch-field

## Plan

`type_switch_to_method` on a switch expression whose selector is an enum of the project, every
case giving a compile-time constant (or `null`): the enum gets a field with each constant's value
(slice 2's edit, `EnumField`) and the switch becomes `e.name()`. First, green: refactor
`EnumField.add` so the edit takes a values map, not `Args` (extract the part after the checks into
a function returning the edits and the members). Then red: goldens `type_switch_enum_field`,
`_enum_field_default`, `_enum_field_shared_case`, `_enum_refused_local`, `_enum_refused_case_null`,
and probes `type_switch_enum_field` (every constant printed) and `type_switch_enum_null_selector`
(the same exception before and after). Then green: a branch in `TypeSwitch.run` for an enum
selector (`TypeSwitch.enumField`), the values checked by `NameCheck` (they move into the enum's
file), the edits through `MemberMove.gate`. A case that computes is refused until increment 6.

## Notes

- read: `TypeSwitch` outline, then `run`, `switchCases`, `usesOnly`, `asReceiver`, `declaration`,
  `fileOf` by `inspect` ranges; `MemberMove.gate`, `NameCheck`, `Declarations.plan` by `names`;
  `EnumField` whole with `cat` (hand: a read the guard would deny; `inspect` of the file would have
  done).
- plan: green first. `replace_text` reordered four lines of `EnumField.add` (the values map, the
  text and `before` as locals above the range), then `extract_function` made
  `field(target, parameter, before, passed, text, type)` returning a record, renamed `Field`
  (`rename`), made package-private with doc comments (one batch of two `replace_text`).
- plan: red. Goldens `type_switch_enum_field` (with `result.txt`), `_field_default` (a `null`
  case), `_field_shared_case` (an enum with a constructor already, a qualified constant),
  `_refused_local`, `_refused_case_null`, `_refused_bare_static`; probes `type_switch_enum_field`
  (a constant with a body, `toString`, `ordinal`, `valueOf`) and `type_switch_enum_null_selector`.
  Not run red: each is refused today by "each case must be a type pattern".
- default (to reopen): a static field named bare in a case's value, declared by another class, is
  refused naming the qualification, over qualifying it in the tool: the smaller change, and the
  type-pattern form leaves the same case to the gate. The sketch's capture golden became this.
- bulk: `add_parameter` of `variable` to `TypeSwitch.usesOnly` (three calls pass "the pattern
  variable"), then one batch: the refusal reads it, `usesOnly` and `qualifiedByOther`
  package-private. One batch of three: the enum branch in `TypeSwitch.run` (`insert_statements`),
  `usesOnly`'s doc, `create_file` of `EnumSwitch` (234 lines).
- refusal: `insert_statements` takes `where`, not `position`; `create_file` takes `file`, not
  `path`. Each named the arguments it takes; resent whole (retry does not cross CLI runs:
  friction log).
- plan: every golden and probe passed on the first run.
- bulk: `Tools.TYPE_SWITCH_TO_METHOD`'s description by one `replace_text`.

## Measured

Not measured: the session-log hook is not loaded in this session. From the notes: 9 tool calls
that wrote (2 refused for argument names), 0 hand edits of Java; reading by `inspect` except one
`cat` of `EnumField`.

## Assessment

Through the tools. The plan held: the refactoring to `EnumField.field` made the new code a
consumer of slice 2's edit, and the field form needed no change to `EnumField` beyond it. One
change that would have helped: `batch` retry across CLI runs (friction log).

## Follow-ups

- Increment 6, the method form: a case that computes, a switch statement, `as`.
- A bare static of the switch's class could be qualified by the tool rather than refused, if runs
  show models stumble on the refusal.
