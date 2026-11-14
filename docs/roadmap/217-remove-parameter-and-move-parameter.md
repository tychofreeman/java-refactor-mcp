---
order: 209
item: 217
status: Done
---

## Item

`remove_parameter` and `move_parameter` judged arguments by a syntax-only `Signatures.pure` that let a division, a cast or a dereference through and took a string's `+` of an object as pure: removing `f(a / b)` dropped an exception, moving two throwing arguments swapped which one threw, and `"" + obj` dropped a `toString` call, unreported (issue #475, code review)

## Status

Done 2026-11-13 (`Signatures.acts`: `Effects.of`'s changes outside lambdas, a new array, and a string's `+` of an object other than a `String` or a box, refused as before; `Signatures.mayThrow`: `Effects.mayThrow()`, reported through `Exceptional.change` for a removed argument, and for a moved one when it and an argument it passes could each throw, or one throws and the other acts; `pure` deleted, `ChangeSignature.pure` renamed `quietAndSafe`. Probes `remove_parameter_argument_throws`, `_to_string`, `move_parameter_arguments_throw`; goldens `remove_parameter_argument_may_throw`, `_preserve_exceptions_refused`, `remove_parameter_refused_argument_to_string`, `move_parameter_arguments_may_throw`. The policy decided report over refuse (session notes, "What a refactoring preserves"). Defaults to reopen (the owner said take the smallest): the `toString` rule in `Signatures`, over a `CALL` in `Effects` for every tool; a move reports pairs with the moved argument only, as the others keep their order)

## Evidence

Issue #475; `docs/sessions/fix-parameter-argument-exceptions.md`
