# preserve_exceptions on every refactoring (roadmap #73)

- **Item:** roadmap #73: one `preserve_exceptions` flag on every refactoring,
  and a separate field in the result naming each change to exceptional
  behavior (the user's decisions of 2026-11-04, open question 0), after #71
  and #72. The user asked for #71-#73 in succession and was away; the
  decisions below are this session's.
- **Date:** 2026-11-04
- **Branch:** preserve-exceptions

## Decisions (made by this session; the user was away)

- **The flag in one place.** `Tools.Def`'s constructor adds
  `preserve_exceptions` to the schema and the allowed arguments of every
  `Kind.REFACTORING` tool. Not chosen: editing eighteen schemas (the thing #71
  and #72 were done first to avoid).
- **The separate field.** `Outcome.exceptional` (a list), shown as a second
  MCP text block (`Server.answerAll`) and on the command line as a section
  after a blank line. Not chosen: MCP `structuredContent`, which a client may
  not show to the model. A batch step's changes are its batch's too.
- **Keep, or refuse.** Under the flag, a tool keeps the behavior when one
  edit does it (`move_member`: `java.util.Objects.requireNonNull(receiver)`,
  written qualified so no import can be ambiguous), and refuses otherwise
  (`Exceptional.change`).
- **First cases.** `move_member`'s null receiver (the case the roadmap
  named), `inline_variable` (an initializer that may throw moved past a
  statement that changes state) and `move_statement` (a statement that may
  throw swapped with one that changes state). "May throw" is syntax only:
  `Effects.Kind.MAY_THROW` (a dereference of a variable or expression, an
  array element, an integer `/` or `%`, a reference cast) and calls and new
  objects. A guard moved past, and code moved into or out of a `try`, are
  not detected yet: no tool moves code across either now (`extract_variable`
  refuses a branch; statements move within their block).
- **An existing golden changed.** `move_statement_names_only_allowed` swaps
  two `print` calls; it now reports that the first may throw after the
  second runs. `names_only` accepts a new order of normal-flow effects; it
  says nothing about exceptions, so the report stays, and the golden gained
  `exceptional.txt`.

## Plan

Not written before the code, as the skill asks: this record was written
after, from the notes below. The derivation followed was:

1. The flag and the field: `Session` (`preserveExceptions`, `exceptional`),
   `Exceptional`, `Outcome` (a third component, the old constructor kept),
   `Tools.Def` and `Tools.call`, `Server.answerAll`, `Cli`, and
   `GoldenTest`'s `exceptional.txt` (a case without it must report nothing).
2. `move_member`: goldens `move_member_null_receiver_reported` and
   `_kept`, red; then `MoveMember.mayBeNull`, the report, and the `keep`
   parameter of `rewrite` (by `add_parameter`).
3. `Effects.Kind.MAY_THROW` and `mayThrow()`; goldens
   `inline_variable_may_throw_reported`, `_refused` and
   `move_statement_may_throw_reported`, red; then a `checkExceptional` in
   each tool.

## Notes

- `plan:` the record was written last, not first: a slip in the skill's
  order. The decisions were made at the time and are written as made.
- `refusal:` `ToolsCallTest`'s test `Def` has a schema without
  `properties`; the constructor threw a `NullPointerException`. Fixed in
  the constructor (it adds `properties` when missing), not in the test.
- `refusal:` `add_declaration` of `InlineVariable.checkExceptional`: 
  `Statement` ambiguous (`java.beans`, `java.sql`, `dom`); sent again with
  `imports`.
- `hand:` a temporary commit and a soft reset to make `bin/jrefactor`
  rebuild its jar from the branch for a probe; nothing was lost, but the
  rebuild rule (a commit newer than the jar) made it the quickest way.
- Probe (`bin/jrefactor` on a scratch copy of the reported golden): the
  section prints after a blank line; with `preserve_exceptions=true` the
  call is `Fees.charge(java.util.Objects.requireNonNull(p), a)`.

## Measured

From `bin/session-report` (before the docs edits):

- tool calls: 38
- through jrefactor: 1 refactoring step (`add_parameter`), 53 edit steps
- refused by jrefactor: 1 (the ambiguous `Statement`)
- by hand: 1 (the commit-and-reset above), then the docs by Python, `sed`, a
  heredoc and Write
- large writes: 8: `Exceptional`, `Outcome`, the three `checkExceptional`s
  and `MoveMember`'s report, the `Effects` visitor additions, and goldens;
  all new
- reads: Bash java 8 (`Tools`, `Server`, `Cli`, `Batch`, `Session`,
  `MoveMember`, `GoldenTest`, `ToolsCallTest`), inspect 1

## Assessment

- **Through the tools or around them:** through, for every Java change.
- **Large writes:** new behavior throughout.
- **Did the plan hold?** The derivation above is what happened; the plan
  was not written first.
- **Reading:** the pipeline classes, to find where a call's flag and result
  pass; that is reading to write the new behavior.
- **One change that would have helped:** `bin/jrefactor` building from the
  working tree when asked (a `--rebuild` flag), for probes of uncommitted
  work. Logged in the friction log.

## Follow-ups

- Detection of a dereference moved past a guard, and of code moved into or
  out of a `try`, once a tool can move code across either.
- `inline_function`: an argument that may throw, evaluated later in the body
  than at the call (`checkTiming` refuses the normal-flow cases; the
  exceptional ones are not reported).
- `extract_variable`: an expression that may throw, moved before an earlier
  part of its statement that changes state (refused today as an effect order
  only when the expression itself has an effect).
