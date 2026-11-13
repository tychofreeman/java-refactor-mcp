# extract_variable as=parameter: a call on the method's own object

- **Item:** roadmap #169 (order 162), the owner's decision of 2026-11-13 on #161's follow-up:
  write a call on the method's own object (`rate(order)`) at each call on that call's object, and
  a static method named without its class on its class where its name alone does not reach it.
- **Date:** 2026-11-13
- **Branch:** extract-variable-parameter-receiver

## Plan

The change: `byParameters` lets a call with no receiver or on `this` through when it is the
method's own object's (the class of the method is a subtype of the called method's class) or a
static method's; the per-call text gets the call's receiver before each such call. Derivation,
before reading bodies: `substituted` takes extra swaps besides the parameters' (a `Swap` record),
`InMethod.written(c, probe)` computes them per call; `perCall` gets the project (`add_parameter`)
and ends with one check, `sameTargets`, that holds what keeps behavior: a steady receiver, the
class or a subclass for an unqualified call, no override below a `super` call, and a probe parse
of the calling file with the argument in place whose calls resolve to the same method or an
override of it, with no not-visible problem. One batch.

## Notes

- read: `ParameterValue` (outline, `byParameters`, `perCall`, `InMethod`, the matcher),
  `Introduce`, `Signatures.addParameter`/`Call`, `NameCheck`, `MethodFamily.of`/`findCalls`
  through `inspect`.
- plan: `NameCheck` compares by declaration key, so it would refuse an override in a subclass,
  which the decision accepts; a probe parse per call compares by key or override instead.
- plan: goldens (8, one the old refused `call_own` made applied) and probes (3) written before the
  code with file tools; red as expected.
- bulk: one batch of 9 steps (`add_parameter` of `project` on `perCall`, 4 `replace_text`, 2
  `replace_declaration`, 2 `add_declaration` of about 280 lines), checked at the end.
- refusal: none from the tools. The build refused a raw-stream `anyMatch` under `-Werror`; fixed
  with `replace_text`.
- plan: `InMethod.at` is also `remove_parameter`'s message text, where a receiver should not be
  written: a golden (`remove_parameter_value_call_refused_other_receiver`) caught it; `inPlace`
  added for that direction.
- bulk: 3 `replace_text` of refusal wording; `overrides` now calls the new `subtype` (refactor
  while green).
- hand: none in `src/main`; golden and probe files with file tools, as the brief says.

## Measured

Not measured: the session-log hook is not loaded here. From the notes: 5 calls into `src/main`
(2 batches of 9 and 2 steps, 3 single `replace_text`s and a batch of 3), 3 for docs; 8 goldens and
3 probes with file tools.

## Assessment

Through the tools. The plan held except for the shared `at`, which a golden of the other
direction caught. The probe parse is the one mechanism for dispatch, overloads and access; it
costs one parse per call of the method, only when the expression has a call.

## Follow-ups

- At `super.m(...)`, writing `rate(a')` would keep behavior where `super.rate(a')` is refused
  (a method with one declaration runs the same body from `super` or `this`); the decision named
  `super.rate`, so it stays refused. Reopen if a session meets it.
- A call on a qualified `Outer.this` in the expression is still refused.
