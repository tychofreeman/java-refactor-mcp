# A method's parameter or return type (roadmap #82)

- **Item:** roadmap #82: change a method's parameter or return type, every
  override following (gorefactor's `change_parameter_type`; Java adds the
  return type). From #125, where a return-type change took an edit at each of
  four callers.
- **Date:** 2026-11-04
- **Branch:** change-signature-type

## Decisions (this session)

- **Options on `change_type`, not a tool:** `parameter` (that parameter's
  type) or `return_type: true` (the method's), with `name` a method
  (`Type.method`, or with parameter types for an overload). Without either,
  `change_type` changes a field, as now. An explicit option over guessing
  from the name: a type may have a field and a method of one name.
- **Every declaration in the family changes**, found as the signature tools
  find it (`MethodFamily.of`), and a lambda that writes the parameter's type.
  Calls are not edited: an argument the new type does not accept, or a use of
  the result the new return type breaks, is refused by the gate, naming it.
  (#125's callers needed `addAll` for `add`: that is a change of what the
  caller does, for an edit.)
- **Refused:** a varargs parameter, a parameter or return type written with
  C-style dimensions (`int x[]`), a constructor's return type.

## Plan

1. Red: goldens `change_type_parameter` (an interface, its implementation, a
   typed lambda, calls), `change_type_return`, `change_type_return_refused_caller`.
2. Green: `SignatureChange.replace(file, node, text)` (`add_declaration`),
   `Signatures.changeType` (`add_declaration`), and the dispatch and schema in
   `Tools` (`replace_text`); `ChangeType`'s "not a field" refusal names the
   options.

## Notes

- `read:` `Signatures.addParameter` and `removeParameter`, `SignatureChange`'s
  methods, and the arguments `MethodFamily.of` reads (`file`, `name`), to
  reuse them.
- `plan:` `land.sh` had checked out `main` after merging #137, so the
  goldens were first created on `main`, untracked; the branch was made from
  there with them, after a pull. Nothing was lost.
- The three goldens were red (two applied wrongly or not at all, one
  error), green after one batch.

## Measured

From `bin/session-report` (this branch's log, before the docs edits):

- tool calls: 8
- through jrefactor: 6 edit steps (2 `add_declaration`, 4 `replace_text`)
- refused by jrefactor: 0
- by hand: 1 (the record), then the docs by Python and `sed`

The 18 `create_file` steps of the goldens are in `main`'s log: they ran
while `main` was checked out (see Notes).

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes.
- **One change that would have helped:** none.

## Follow-ups

- Callers whose code must change with the type (#125's `add` to `addAll`)
  still need edits; a batch of `change_type` and `replace_text` with check
  `end` does it.
- A method reference to the method is not checked here; the gate is.
