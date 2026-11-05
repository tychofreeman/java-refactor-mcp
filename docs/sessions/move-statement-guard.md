# The rest of #75: guards and try resources

- **Item:** roadmap #75, the follow-ups of `docs/sessions/exceptional-more.md`: a
  dereference moved past a guard by `move_statement`, and `extract_variable` out
  of a `try`. The user's order of 2026-11-04 ("then the rest of #75").
- **Date:** 2026-11-05
- **Branch:** move-statement-guard

## Decisions (this session)

- **A guard is a statement that may leave the block early:** a `return`, or a
  `yield`, `break` or `continue` that lands outside it (not in a loop, switch or
  labeled statement inside it), outside lambdas and classes. A `throw` is not one:
  `move_statement` already refuses a bare jump, and a guard that throws creates
  an object, so the strict rule or the existing may-throw check covers it.
- **Swapping a guard with a statement that changes state is refused,** not
  reported: it changes normal flow (`if (done) return; total++;` swapped counts
  when done). This closed a gap in the normal-flow rules, found while looking for
  the exceptional case. A statement that only may throw is reported through
  `Exceptional.change`.
- **`extract_variable` "out of a try" happens only from a try's resources:** the
  declaration goes before its statement in the same block, so it stays in any
  enclosing `try`; but an expression in a try-with-resources header moves out of
  that try's `catch` clauses and `finally`. Reported when the try has either.
- **The other two parts of the row were already refused** by the normal-flow
  rules, checked by a probe: an `inline_function` argument that may throw, used
  after a change in the body (`p.cost` reads state), and an extracted expression
  after an earlier change (`checkEarlierEffects`).

## Plan

1. Red: goldens `move_statement_refused_guard`,
   `move_statement_guard_may_throw_reported`, `move_statement_inner_break_allowed`
   (green from the start, against over-refusing), `extract_variable_try_resource_reported`.
2. Green: `checkGuard`, `leaves`, `caught` in `MoveStatement`;
   `checkTryResources` in `ExtractVariable`; `move_statement`'s description.

## Notes

- Each red golden failed on what it pins; the inner-break golden first had a
  wrong line in its args (a refusal naming line 11), fixed with `replace_text`.
- The extract golden was red only on its report: the edit already matched.

## Measured

All Java through jrefactor: goldens by `create_file`, the checks by
`add_declaration` (each declared as a stub by the call that used it first, then
replaced) and `replace_text`; no refusals.

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes; the normal-flow refusal was added to it on the way.

## Follow-ups

- None for #75. A guard that is a call (`Objects.requireNonNull(p)`) is covered
  by the strict rule and by `checkExceptional` (a call may throw).
