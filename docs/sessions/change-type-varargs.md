# change_type to and from varargs

- **Item:** issue #614 (friction log), roadmap item 502
- **Date:** 2026-11-15
- **Branch:** change-type-varargs

## Plan

Read `T...` in `Signatures.changeType` as the element type plus the varargs form: parse the
element, check the parameter is last, and write the type and its `...` as one edit (the `...`
sits between the type and the name). Refuse `T...` for a return type and a field. Calls stay to
the gate.

## Notes

- plan: eight goldens first; red: "does not parse" and, once past it, "has varargs, which these
  tools do not change yet" (`MethodFamily.of`), which the issue did not name.
- `add_parameter` `varargs` (boolean, value false) on the four-argument `MethodFamily.of`: one
  declaration and four calls in three files; `replace_text` of the check and of its doc comment.
- `replace_declaration` of `Signatures.changeType`; its new helper `varargsElement` was declared
  as a stub, then `add_declaration` replaced it.
- `replace_text` for the field refusal in `ChangeType.run`, and one for the `true` passed.
- refusal: `replace_text` with `old_file=-` and `new_file=-` both from stdin ("only one argument
  can come from stdin"); sent `old` inline instead.
- hand: goldens and docs (plain files).

## Measured

Not measured (the session-log hook is not loaded here). From the notes: 6 tool edits to Java,
0 by hand.

## Assessment

Through the tools. Decision taken as the smallest: `change_type` alone passes `varargs`, for a
parameter or a return type, since it edits no call; the tools that rewrite calls by position
still refuse a varargs method. Annotations written before a `...` (`int @A ... xs`) are dropped
when it becomes `int[]` or `int`; kept when it stays varargs.

## Follow-ups

- The gate's refusal for a parameter type change begins "changed the type of ...", the result's
  wording, not "changing".
