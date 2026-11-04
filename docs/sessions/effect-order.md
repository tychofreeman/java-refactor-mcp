# Effect order and name capture in inline and extract (roadmap 60, 61)

**Item:** the three tools that move code refuse what the probes changed while type-checking
(`docs/sessions/stress-new-tools.md`). The gate cannot see these: each case compiles.
**Source:** roadmap rows 60 and 61, chosen by the user on 2026-11-04 ("Effect order in inline and
extract (Recommended)").
**Date:** 2026-11-04

## Plan

Refuse, not rewrite. Each rule is a precondition on the code moved:

- `inline_function` (`InlineFunction.checkArguments`, `usesMembersOf`): an argument with an effect
  for a parameter the body does not use (dropped); two arguments with effects (order); an argument
  whose type differs from its parameter's, and whose call therefore converts (`half(3)`); a body that
  names a member of its class when a call is in another class (`Lib.scale` reading `FACTOR`).
- `extract_variable` (`ExtractVariable.checkEarlierEffects`, `checkFieldName`): an effect earlier in
  the statement that the read would move ahead of (`i++ + (i * 2)`); a new name that a field of the
  enclosing class or its superclasses has (`total`).
- `inline_variable` (`InlineVariable.checkShadowed`): an initializer name that a later declaration in
  the block hides at a use (`int a = n;` then `int n = 2;`).

The rules are conservative: a safe case of the same shape is refused too.

## Golden cases (written red first, then passing)

- `inline_function_refused_dropped_effect`, `inline_function_refused_two_effects`,
  `inline_function_refused_conversion`, `inline_function_refused_other_class`
- `extract_variable_refused_effect_before`, `extract_variable_refused_hides_field`
- `inline_variable_refused_shadow`

Before the change, all seven failed (seven red). Two were red for a wrong reason first (a wrong
line number in my own args), which I fixed before the rule was written.

## Notes

- `hand:` the seven goldens' `in/` and `out/` files were written by hand, and the three tools'
  checks by Edit, as no tool adds a refusal. The Edit calls were the rule itself, not a rewrite.
- `plan:` the planned "conversion" rule refuses every argument whose type is not equal to its
  parameter's type, not only numeric conversions. A subtype argument (a `String` for an `Object`)
  is refused too: the conservative choice, unmeasured on real code.
- `refusal:` the first conversion message did not contain the golden's "converts to"; reworded in
  the message, not the golden.

## Measured

Not measured (the session log does not cover this branch; the goldens are the measure).

## Assessment

Through the tools: the three refusals were the change the probes asked for, made by Edit in three
classes that the tools already share (the same `Refusal` path). No rewrite was done by hand.
The gap: the stress run cannot see these (every case compiles), so the goldens and the probe list
are the only check. A rule that refuses every non-equal argument type is broader than the probe
needed; the cost is refusals on safe calls with subtypes, which no golden covers yet.

## Follow-ups

- A subtype argument (`String` for `Object`) for `inline_function`: refused now; allow it once a
  golden shows the body behaves the same.
- A check that every name in moved text resolves to the same binding after the edit (roadmap 60's
  general form), once a second tool needs it.
