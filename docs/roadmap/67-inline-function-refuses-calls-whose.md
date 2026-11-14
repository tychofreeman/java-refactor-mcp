---
order: 60
item: 67
status: Done
---

## Item

`inline_function` refuses calls whose inlined body would compile and behave differently: an argument with effects for a parameter the body does not use (dropped), two arguments with effects that the body reads in another order (reordered), an argument the parameter's type converts (`half(3)` with `double x` gives `3 / 2`), and a name in the body that means another declaration at the call (`FACTOR` of the caller's class)

## Status

Done; the gaps (an effect before an argument's use, a use in a lambda or branch) added by the local session, `docs/sessions/inline-function-effect-gaps.md`

## Evidence

Found by probes after `docs/sessions/stress-new-tools.md`; each applied and type-checked. `InlineFunction`. A check that every name in moved text resolves to the same binding after the edit would cover the last case for every tool that moves code
