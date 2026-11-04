# Signature changes refuse a method that lambdas implement

- **Item:** a gap from `docs/sessions/stress-refactorings.md` (#70):
  `add_parameter` of `FailableDoubleSupplier.getAsDouble()` and
  `remove_parameter` of `FailableDoubleToLongFunction.applyAsLong(double)` in
  Commons Lang were refused only by the gate, with a refusal that says to
  choose a value that resolves. The user asked (2026-11-03) for the stress
  test's three gaps; this is two of them (and `move_parameter`), the third
  (`move_type`) is its own item.
- **Date:** 2026-11-03
- **Branch:** refuse-lambda-implemented

## Plan

The change, at the highest level: a lambda implements a method without
declaring it, so a signature change cannot reach it; the signature tools
refuse such a method up front, as they refuse one used as a method
reference, and say how to make it reachable. For `move_parameter` this
matters more than a clearer message: when the two parameters have the same
type, the lambdas still compile with their parameters swapped, and the
gate cannot see it.

Derivation, in order:

1. Red: golden `sig_refused_lambda_add`, `sig_refused_lambda_remove`,
   `sig_refused_lambda_move` (an interface `Op` with `int apply(int a, int
   b)`, a lambda `(a, b) -> a` assigned to it): each refused with "Op.apply(int,
   int) is implemented by a lambda (com/example/Calc.java, line 6)". Today
   add and remove are refused by the gate, and move applies.
2. Green: in `MethodFamily.findCalls`, a `LambdaExpression` whose
   functional interface method is in the family is refused: "rewrite it as
   an anonymous class first, which this tool then changes with the rest".
3. The three tools' descriptions and the tool contract list the refusal.

## Notes

- bulk: the golden cases (four, 21 files, new).
- test: red as planned: add and remove refused by the gate, move applied.
  The add case first failed on its fixture (`value` belongs in
  `value.txt`), fixed.
- refusal: the `findCalls` edit: my `old` had two spaces too many, read off
  `inspect`'s separator again. Resent.
- plan: a fourth case, `sig_refused_lambda_elsewhere`: the family search
  parses only files that contain the method's name (#44), and a lambda
  passed as `User.use((a, b) -> a)` names neither `apply` nor `Op`. It
  failed (moved silently); fixed by parsing the other files with `->` when
  the family has an abstract interface method.
- found: the Commons Lang stress seed that showed the gaps now refuses all
  three up front, and a fourth: `move_parameter` of
  `FailableObjIntConsumer.accept(T, int)`, which the run before had
  applied, passing the gate and the stress test with the lambdas'
  parameters swapped.

## Measured

`bin/session-report` (`.claude/session-log/refuse-lambda-implemented.jsonl`):

- tool calls: 23
- through jrefactor: 0 refactoring steps, 25 edit steps
- refused by jrefactor: 1
- by hand: 1
  - Bash: cd /home/cwfreeman/dev/ai-playground/java-refactor-mcp && cat > docs/sessions/refuse-lambda-implemen...
- refused, then done by hand: 0
- large writes (20 lines of Java or more in one call): 2
  - replace_text src/main/java/io/github/tychofreeman/jrefactor/MethodFamily.java: 21 lines
  - replace_text src/main/java/io/github/tychofreeman/jrefactor/MethodFamily.java: 25 lines
- written to scratch files: 0
- denied or failed (no result): 1
  - Bash: R=$(bin/session-report 2>/dev/null | tail -30); cat >> docs/sessions/refuse-lambda-implemented.md <<...
- reads: Bash java 3, inspect 3

## Assessment

- **Through the tools or around them:** all Java and fixtures through
  java-refactor-mcp; by hand: the docs and this record.
- **Did the plan hold?** Yes, plus the fourth case, which the name filter
  of #44 made necessary.
- **What went badly:** the stress test counted the swapped move as a
  success: it checks compile errors and undo, not behavior. A change that
  compiles and changes meaning is outside what it can see.
- **One change that would have helped:** none new.

## Follow-ups

- Instead of refusing, the tools could change the lambdas too: add a
  parameter name, drop an unused one, reorder them.
- The stress test cannot see a change of meaning that compiles; running
  the project's tests after each applied call would (Commons Lang's tests
  are not in the sources jar).
