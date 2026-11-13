# extract_variable as=parameter: the new parameter after the last one read

- **Item:** roadmap #172 (order 165), the owner's decision of 2026-11-13: place the new parameter
  right after the last parameter the expression reads, in place of the refusal for an argument
  with effects after a re-read one (probe `extract_variable_parameter_argument_between`).
- **Date:** 2026-11-13
- **Branch:** parameter-after-last-read

## Plan

`Signatures.addParameter` already takes `before` (a parameter's name; `MethodFamily.index`), and
inserts at that index in declarations, lambdas and calls. So: `Introduce.parameter` computes the
place from `InMethod.uses()` and passes `before`; `InMethod.at` (its index) is that place; the
re-read loop in `perCall` checks only the arguments before it. One batch of four `replace_text`.

## Notes

- read: `perCall`'s loop, `MethodFamily.index`, `Introduce.parameter`'s request, the probe.
- plan: an expression that calls or may throw stays last: placed earlier, its call or its
  exception would come before the later arguments' effects, where the body had it after.
- plan: the owner's example of a still-refused case, `run(tick(), step)`, is no change: the
  parameter and the new argument both read `step` after `tick()`. The golden
  `..._effect_before` pins it as applied; `..._between` pins the refusal for an argument with
  effects between the read one and the new place.
- refusal: the batch, a duplicate local name (`added`, already a local of the method); renamed in
  the request file and resent whole (the CLI does not keep a refused batch: friction log, earlier).
- plan: goldens (6) and the flipped probe before the code; a golden's `q + r + z` has no `q + r`
  node (JDT's extended operands), written `(q + r) * z`.
- hand: none in `src/main`.

## Measured

Not measured: the session-log hook is not loaded here. 2 batch calls into `src/main` (one refused),
1 batch for docs; 6 goldens and 1 probe with file tools.

## Assessment

Through the tools; the existing `before` made it a small change. The override and lambda goldens
pass as the plan expected: the family's declarations and the lambda get the parameter at the
same place.

## Follow-ups

- An expression with a call or one that may throw could also go earlier where no later argument
  at any call has effects; left last.
