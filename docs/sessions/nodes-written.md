# "Is this expression written?" through Nodes.written

- **Item:** issue #510 (code review, group 4 of #532): write detection copied despite
  `Nodes.written`.
- **Date:** 2026-11-14
- **Branch:** nodes-written

## Plan

Swap each copy for `Nodes.written` (a delegate and `inline` where it is a method, `replace_text`
where it is a condition). The copies are the same rule, so no behavior change was expected; the
goldens are the check. Before swapping, look for a case every copy gets wrong.

## Notes

- read: `inspect` `pattern` `Operator.INCREMENT` for the copies; the statement-expression and
  loop-step tests ask another question and stay.
- plan: every copy, `Nodes.written` too, missed a parenthesized target. Probed with the CLI:
  `inline` wrote `(x)++` at the call (silent), `convert_loop` wrote `(value) = 0` (silent),
  `extract_variable` `as=parameter` passed the value before `(qty)++` (silent), `extract_type`
  refused only by the gate. Probes and goldens written first; all seven failed.
- refactor while green: the swaps (two batches), then the fix in `Nodes.written`.

## Measured

Not measured (no session-log hook); calls are in `JREFACTOR_LOG`. Java changes in four tool calls
(two batches, `replace_declaration`, `add_declaration`), none by hand.

## Assessment

Through the tools. The plan held; the review's duplication hid a bug the copies shared.

## Follow-ups

- `EncapsulateField.isIncDec` and the statement-expression tests could share a helper of their
  own; not the same question.