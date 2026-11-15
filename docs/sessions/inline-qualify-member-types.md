# inline qualifies member types of the declaring class

- **Item:** roadmap item 518, issue #673 (the owner's list of 2026-11-15, second item)
- **Date:** 2026-11-15
- **Branch:** inline-qualify-member-types

## Plan

At the level of the change: `inline` writes the body's text at each call, with edits at
positions of the declaring file (parameters, receiver, shadowed fields). A member type named bare
is one more such edit, the one `move` already makes (`MoveMember.qualifyMemberTypes`). So: expose
that helper, tell `replacementFor` whether the call is inside the declaring class, and add the
helper's edits to the receiver's edits when it is not. Refactor first (green): the helper
package-private and the new parameter, passed `p.equals(file) && Nodes.within(site, type)` (the
test `run` already makes for a body that uses the class's members); then the goldens red; then
one statement.

## Notes

- read: `inspect` of `InlineFunction` (outline, `run`, `replacementFor`, `substituted`) and of
  `MoveMember.qualifyMemberTypes` and `reached`; `NameCheck`'s refusal.
- refusal: `extract_variable` of `p.equals(file) && Nodes.within(site, type)` (to name it before
  `add_parameter`): it sits right of `&&`, so it would run at another time. Retried the batch
  without that step, with the expression as `add_parameter`'s `value`: applied.
- plan: `qualifyMemberTypes` takes the whole method; `substituted` keeps only edits inside the
  range it copies, so the helper is reused unchanged.
- red: goldens `_member_type` and `_other_package` errored, the probe refused (`Stack` would mean
  `java.util.Stack`); `_inside` passed already.
- green: one `insert_statements`.
- Not done: a member type of an outer class of the declaring class (the issue's "or its outer
  classes"): `inline` takes a method of a top-level class only (`Opened.classOrRecord`), so there
  is none; `reached` covers the class and its superclasses.

## Measured

Not measured: the session-log hook is not loaded here. Java written through the tools: 6 calls
(a batch of `replace_text` and `add_parameter`, `insert_statements`, `replace_text` of a Javadoc,
docs through `replace_text`), 0 hand edits; formatting by `spotless:apply`.

## Assessment

Through the tools; the plan held. The one refusal was right (a hoisted `&&` operand).

## Follow-ups

None.
