# extract_variable as=parameter: cast an argument of another type

- **Item:** roadmap #171 (order 164), the owner's decision of 2026-11-13: where a call passes a
  primitive of another type than the parameter the expression reads, write the argument cast to
  the parameter's type in place of #161's refusal.
- **Date:** 2026-11-13
- **Branch:** parameter-value-cast

## Plan

The refusal in `ParameterValue.perCall` becomes a question, `cast(declared, given)`: no cast, a
cast, or none possible (refused as before). `substituted` writes `(T) arg` for each parameter
occurrence where the types differ, given the method's parameters (the extraction's `written`
passes them; `inPlace`, the other direction's message text, does not); `occurrences` marks a
name in receiver position, where the cast needs parentheses. One batch.

## Notes

- read: `perCall`'s type rule, `occurrences`, `substituted`, the two `argument_type` probes, through
  `inspect` (pattern and ranges).
- plan: `remove_parameter` `value` cannot take a cast: the call computed the removed argument in
  its own type (`x * y` in `int`), the body would compute it in the parameters' type, and casting
  the body's parameters would be wrong for the calls that pass their own type. Its probe stays
  refused; the owner's message asked for it applied, so this is said in the PR.
- plan: goldens (3: casts of int, char and a sum to long, and none for long; a boxed receiver;
  refused for a non-java.lang type) and the flipped probe, before the code.
- bulk: one batch of 7 steps (3 `replace_text`, `replace_declaration` of `substituted`,
  `add_declaration` of `cast`, 2 `replace_text`).
- refusal: two goldens could not be written as first planned: `add_parameter` does not find a
  generic method (`show(T)`) or one with a parameterized parameter (`show(Comparable<Integer>)`)
  by its label (friction log). The refused golden takes `Serializable`.
- hand: none in `src/main`.

## Measured

Not measured: the session-log hook is not loaded here. 4 calls into `src/main` (a batch of 7,
3 `replace_text`), 1 batch of 4 for docs; 3 goldens and 1 probe with file tools.

## Assessment

Through the tools; the plan held. The cast is exact for the conversions a call makes (widening,
boxing, unboxing), all of which a cast context allows.

## Follow-ups

- Casts to other reference types (with the import fix) and to type arguments are left refused.
- `add_parameter`'s lookup of a generic method, or one with a parameterized parameter type (friction
  log, 2026-11-13).
