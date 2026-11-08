# The signature tools find a method by line

- **Item:** roadmap #130, its last part (gorefactor's `docs/parity.md` section 4: line/column
  addressing on the signature tools). The user chose (2026-11-08): `line`, of the declaration or of
  any call, as an alternative to `name`, with `column` for two calls on a line; over the
  declaration's line only, and over leaving it until the friction log asks.
- **Date:** 2026-11-08
- **Branch:** signatures-by-line

## Plan

The change: `add_parameter`, `remove_parameter`, `move_parameter` and `change_type` (with
`parameter` or `return_type`) take `file` and `line` in place of `name`; the line holds the
method's declaration or a call of it, and the method may be declared in another file.

Derivation:
1. Green: `Args.with(key, value)`, so a resolver can hand the tools the arguments they take now.
2. Red: goldens `add_parameter_by_call_line` (a call in another file), `remove_parameter_by_line`
   (the declaration's line), `change_type_parameter_by_column` (two calls on a line, `column`
   picks), `add_parameter_refused_two_calls_on_line` (no `column`: the columns named).
3. Green: `ByLine.named(project, args)`: without `name`, with `line`, the declaration whose name
   is on the line, else the one call there (method, constructor, `super`), by its binding to the
   declaring file (`TypeSwitch.fileOf` of its top-level type) and the member's label there;
   returns the arguments with that `file` and `name`. The four tools call it first; their schemas
   gain `line` and `column`, and `name` is no longer required.
4. Docs: descriptions, contract, roadmap, parity.

## Notes

- read: `Signatures.addParameter` to `changeType` (61-240), `MethodFamily.of`, the four `Def`s,
  `Args`, `Declarations.find` and `label`, `Signatures.label`, `TypeSwitch.fileOf`,
  `Nodes.columns`: where the tools read `name`, and what a name must look like for `find`.
- read: two golden fixtures with `cat -n` (resources), to count the columns of the calls.
- plan: the refusal's wording followed `Nodes.columns` ("14 and 27"), so the golden changed to it.
- bulk: `ByLine` (137 lines, `create_file`), new behavior; the goldens (resources), new tests.

## Measured

From `bin/session-report`: 21 edit steps through jrefactor (one batch of eleven for the code, one
of four for the descriptions, one of four for the docs), no refusals; by hand only the record and
test resources; reads: 6 `inspect`, 2 `cat` of golden fixtures.

## Assessment

- **Through the tools or around them:** through them; the eleven-step batch passed every test on
  the first run.
- **Did the plan hold?** Yes. `ByLine` returns the arguments with `file` and `name` set, so
  nothing below the tools changed.
- **One change that would have helped:** none.

## Follow-ups

- A line whose call resolves to a library method is not found (only project methods change);
  the refusal says the line calls no project method.
- `replace_declaration` has no `line` either (contract: gorefactor's `line`/`column` there are not
  built).
- gorefactor's `docs/parity.md`: #130's three parts are no longer behind (its own PR).
