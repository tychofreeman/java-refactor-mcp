# A lambda argument to an undefined method

- **Item:** the friction entry of 2026-11-04 (`docs/friction-log.md`): a stub
  for a call with a lambda argument types the parameter `Object`, which is not
  a functional interface, so the edit is refused as a compile error. Chosen by
  the user from `what's next?`, 2026-11-04.
- **Date:** 2026-11-04
- **Branch:** stub-lambda-argument

## Plan

The change, at the highest level: a stub whose argument is a lambda gets a
parameter type the lambda can be passed to, instead of `Object`.

Operations, in order:

1. Red golden: `add_declaration` of a method calling an undefined
   `store(() -> 1)`. Expected under option A below: a stub with a
   functional-interface parameter, and the edit added.
2. In `Stubs.params`, the argument's type comes from
   `typeName(argument.resolveTypeBinding())`, which is null for a lambda, so
   the stub gets `Object`. Replace that for a lambda argument, by the choice
   the user makes (below).

Decision needed (two options; the user chooses):

- **A. Functional interface by arity (recommended).** A lambda with no
  parameters gets `java.util.function.Supplier<Object>`, one parameter
  `Function<Object, Object>`, two `BiFunction<Object, Object, Object>`. It
  compiles, so the edit goes through in one call. Cost: a guess; a method
  that expects `Runnable` or a project interface is then refused, and the
  model must change the type by hand.
- **B. Refuse, naming the choices.** The refusal says the lambda needs a
  named functional interface and that the method must be declared first, as
  an unknown-type argument is. Cost: no guess, but every lambda call costs an
  extra turn, which is the cost the stubs were built to remove.

## Notes

- `decision:` the user chose option A (functional interface by arity), asked
  with the two options above, 2026-11-04.
- `read:` `Stubs.params`, to find where an argument's type comes from; for a
  lambda `resolveTypeBinding` is null, so `typeName` returns `Object`.
- Red golden `stub_lambda_argument`: refused, "The method store(Object) ... is
  not applicable for the arguments (() -> {})", as the friction entry says.
- `hand:` `lambdaType` and `functional` in `Stubs` were written by Edit.
- Second golden `stub_lambda_two_arguments` covers the two-parameter case,
  which the first golden does not reach.

## Measured

Goldens `stub_lambda_argument` and `stub_lambda_two_arguments`: red before,
green after. `./mvnw verify` passes; spotless clean. The session-log hook was
not measured for this session.

## Assessment

- **Through the tools or around them:** the change is in `Stubs`, which the
  tools do not edit; the goldens went through `add_declaration`.
- **Did the plan hold?** Yes, with option A as chosen. The red golden
  reproduced the friction entry before the change.
- **Reading:** `Stubs.params`; the lambda's parameter count.

## Follow-ups

- Option A is a guess. A method expecting `Runnable` or a project interface
  is still refused, and the model changes the type by hand.
- A lambda whose body uses its parameter as a specific type (`x -> x.size()`)
  fails to compile against `Object`; the stub is then refused.
- A lambda with more than two parameters still gets `Object`, and is refused.
