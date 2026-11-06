# add_result

- **Item:** roadmap #124, a tool Java lacks (gorefactor's `docs/parity.md` section 3). The user's
  choice (2026-11-06): a record result, over deferring it for want of a friction entry.
- **Date:** 2026-11-06
- **Branch:** add-result

## Plan

`add_result` (`file`, `name` Type.method, `type`, `result`): the method returns one more value,
which its callers ignore, so behavior is unchanged and the model then fills the value.
- A method that returns a value returns a new record `<Method>Result(<old type> value, <type>
  <result>)`, declared after it (as `extract_function` names its two-result record); each `return
  e;` becomes `return new TotalResult(e, 0);` (the type's zero: 0, false, null); each call whose
  value is used reads `.value()`; a call as a statement is left.
- A void method returns `type`: each `return;` becomes `return 0;`, and one is added at the end
  of a body that can reach it.
Refused: an override or overridden method, a method reference to it, a result named `value`, a
record name in use; and what the type check finds.

Derivation: goldens first (value; void); `AddResult` from `declaration`, members by
`add_declaration`; the `Def` (a refactoring) in `Tools`.

## Notes

- read: `Holding`, `MemberMove.member`, `typeNamed`, `gate` (reused); nothing else.
- refusal: the `add_declaration` of `AddResult`'s members, for `Expression` (three candidates in
  a new file); resent with `imports` (193 lines sent twice). Third time this session that a new
  file's first members need the ambiguous JDT names passed by hand; the friction-log entry of
  2026-11-06 covers it.
- bulk: `AddResult` (193 lines): new behavior.
- Both goldens passed first time.

## Measured

Through jrefactor: one batch refused, then resent; the docs by script (Markdown); by hand the
goldens (resources) and this record.

## Assessment

Through the tools. The import refusal in a new file keeps costing a resend of the whole member
text: an `imports` default for a file that is new (the package's other files' imports) would
remove it.

## Follow-ups

- add_result on an override family (with `MethodFamily`), and on a recursive method.
