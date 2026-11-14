# A guard keeps a division or a dereference only when it tests the operand

- **Item:** roadmap #173 (order 166), the user's decision of 2026-11-13 narrowing the
  2026-11-04 exceptional-behavior policy for ArithmeticException and NullPointerException
- **Date:** 2026-11-13
- **Branch:** guard-exceptions

## Plan

The rule is the behavior gate's: `Behavior` refuses code that may throw leaving a guard in two
places, `checkPlace` (out from under an `if`, `?:`, `&&`/`||`, loop or switch) and `checkExits`
(above an early exit, and the same swap seen from the guard's side). Each asks one new question
before refusing: does everything that may throw fail only on a divisor or a receiver, and does no
guard left behind test one of them? Then report through `Exceptional.change` instead. The
questions go in one new class (`Guards`: `operands`, `left`, `tests`, `same` by binding key),
so no tool changes. Goldens for both sides, a probe for the reported move, a unit test for the
table of tests.

## Notes

- read: `Behavior`, `Effects` (MAY_THROW marking, `sometimes`, `later`), `Exceptional`,
  `MoveStatement`'s `Move`, through `inspect`.
- plan: red first: eight goldens and one probe; five failed as expected.
- refusal: a batch's steps take `args`; my first batch put the arguments beside `tool`. Fixed and
  resent.
- refusal: `batch content_file=-` is not taken; sent the batch as `@file`.
- plan: `extract_variable_parameter_guarded` stopped refusing: `Guards.left` skipped a guard
  whose node held the landing, and `Introduce` lands on the guarding `if` itself. Fixed to ask
  whether the part of the guard that holds the code holds the landing.
- read: the guard-below branch of `checkExits` is reached by no golden: `move_statement` always
  declares the later statement as the one that moves, so `direction=down` of a guard is the
  up-move of the thrower (`move_statement_guard_down_*` pin that).
- Java written through `create_file` (`Guards`, `GuardsTest`), `replace_declaration` and
  `replace_text` batches (`Behavior`, `Guards`); goldens, probe and docs by hand (plain files).

## Measured

Not measured: the session-log hook is not loaded here.

## Assessment

Through the tools for all Java. No existing golden or probe changed its expectation: the three
guard goldens before this (`extract_variable_refused_guard`, `extract_variable_parameter_guarded`,
`move_statement_refused_guard_may_throw`) each test the operand and stay refused.

## Follow-ups

- The guard-below branch of `checkExits` has no golden that reaches it; a tool that moves a
  leaving statement later would.
- Defaults to reopen (in `docs/design.md`, Decisions): any constant number counts for a divisor's
  test; a switch on the operand counts; catch clauses, lambdas and anonymous classes still refuse.
