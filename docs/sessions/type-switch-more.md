# type_switch_to_method: statements, default, instanceof chains

- **Item:** the follow-ups of #127 (`docs/sessions/type-switch-to-method.md`), the fourth of the
  user's "do all 4" (2026-11-06).
- **Date:** 2026-11-06
- **Branch:** type-switch-more

## Plan

`type_switch_to_method` takes, at `line`, also:
- a switch statement of arrow cases (an expression statement or a block each): the method is
  void, each case's statements its body, the switch replaced by `s.name();`;
- a `default` (or a chain's final `else`): its body becomes `S`'s own implementation, a default
  method on an interface or a concrete one on an abstract class, the selector (a name) as `this`;
- an `if` chain of `s instanceof T v` conditions (statements, as a switch statement).
A body may declare its own locals; a `return`, `break`, `continue` or `yield` that leaves it is
refused (it would leave the new method instead).

Derivation: goldens first; the cases found from three kinds of node into one `Case` list; the
member text built from an expression or statements; `usesOnly` taking the body's own locals.

## Notes

- read: `TypeSwitch`'s outline (mine from #127); `javap` for `PatternInstanceofExpression`.
- refusal: the batch, for `usesOnly` and `asReceiver` still taking an `Expression`: steps the plan
  named and I left out. I tried the new `retry` with only the two missing steps; the server was
  the build from before #247 (no reconnect since), so `retry` was "not taken", and the whole batch
  (about 330 lines) went again. Exactly the cost #247 removes, once the server is current.
- bulk: the rewritten members (about 330 lines, twice): new behavior, the cases now of three node
  kinds; the helpers `declaration`, `fileOf`, `isVariable`, `qualifiedByOther` kept as they were.
- A block body came out over-indented (its first line stripped, the rest not); fixed by keeping
  each line's indentation (`replace_text`).

## Measured

Through jrefactor: one batch refused, one `retry` refused (old server), the batch resent, one
fix, the docs; by hand the goldens (resources) and this record.

## Assessment

Through the tools. The resend shows why `retry` matters, and that a session must reconnect after
a server change to use it.

## Follow-ups

- Values of the method passed as parameters of the new method (today refused).
- Guards (`case Circle c when ...`).
