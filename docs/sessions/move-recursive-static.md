# move of a static method that calls itself

- **Item:** issue #356 (roadmap #140), from the `convert_loop` work (friction log 2026-11-11,
  "move of a recursive static method refused", and 2026-11-10, "a parameter named receiver"). The
  user asked for the open issues to be fixed (2026-11-11).
- **Date:** 2026-11-11
- **Branch:** move-recursive-static

## Plan

The change: `move` applies its receiver rules only to an instance method. A static method has no
receiver, so its own parameter named `receiver` and its calls of itself are not refused, and a
call of itself moves as it is: unqualified it resolves in the new class; `Old.m(...)` loses its
qualifier.

Derivation:
1. Red: goldens `move_member_static_recursive`, `_recursive_qualified`,
   `move_member_static_param_named_receiver`; `move_member_refused_instance_recursive` pins the
   refusal that stays.
2. Green: `MoveMember.checkBody` gates both refusals on an instance method.

## Notes

- read: `MoveMember.checkBody`, `movedEdits` and the call-site collection in `run` (136-165,
  503-675): where the refusals are, and how calls in and of the moved body are written.
- plan: three more edits than planned, each found by a golden: the call-site rewrite skips calls
  inside the moved method (it rewrote the recursive call inside the text it removed, a syntax
  error); `movedEdits` leaves an unqualified call of itself alone (it would have qualified it with
  the old class); a qualified call of itself loses the qualifier (the issue allowed either; one
  form, unqualified, needs no target name in `movedEdits`).
- refusal: a doc batch, `old` misquoted; `retry` from the CLI found no refused batch (one process
  per call; friction log), so the batch was sent whole.
- Every edit through `bin/jrefactor` (the MCP server is disconnected): two batches of
  `replace_text`; goldens by shell (resources).

## Measured

From `bin/session-report`; see the PR.

## Assessment

- **Through the tools or around them:** through them; no Java by hand.
- **Did the plan hold?** The refusals were the visible half; the call writing behind them was
  never reached before, and needed three fixes.
- **One change that would have helped:** none.

## Follow-ups

- An instance method that calls itself is still refused; its call could pass `receiver`
  (`m(receiver, ...)`), when the call has no other receiver.
