# extract_variable as=parameter: all_occurrences, and a call in the expression

- **Item:** roadmap #161 (order 154): the two gaps of gorefactor's form of `replace_parameter` in
  slice 4 of `docs/sessions/generators-sketch.md`, `all_occurrences` and a call in the expression,
  reusing `ParameterValue` (the handover from the earlier agent of this track, item 4).
- **Date:** 2026-11-13
- **Branch:** extract-variable-parameter-all

## Plan

The mirror of #147 and #148: what `remove_parameter` `value` computes first in the body,
`extract_variable` `as=parameter` moves out to every call. Derivation, before reading bodies:
build an `InMethod` from the selected expression (the record #147 made for the other direction),
so `uses`, `acts`, `readsFirst` and `at` serve both; move the per-call rules into
`perCall(InMethod)`; let `addParameter` take the caller's edits so the occurrences are replaced in
the same change (it had re-found one occurrence by its text after a first write); declare the move
of an expression with a call to the behavior gate as a `Move` to the body's first statement. One
signature change (`add_parameter` of a hook), then one batch.

## Notes

- read: `Introduce`, `ParameterValue`, `Behavior.flow`/`checkCrossed`/`checkPlace`,
  `Signatures.addParameter` and `removeWithExpression` through `inspect` with ranges and `names`.
- plan: worked out the moves: the expression's call now runs after every argument (the new one is
  last) and before the body, so the body's code before it is crossed (`Behavior.flow` does that,
  with branches, loops, exits and tries); an argument the expression reads is read again at the
  call, after the later arguments and the expression's earlier calls.
- plan: two of #147's rules were missing in this direction too, without a call: a primitive of
  another type (probe `extract_variable_parameter_argument_type`), and an argument with effects
  after one the expression reads again (probe `extract_variable_parameter_argument_between`,
  which printed another value before the fix). Both went into the shared `perCall`.
- plan: `Behavior.flow` refuses an expression in a loop that reads variables; right for a call,
  too strict for a fixed expression of parameters. So it runs only for an expression that calls or
  may throw (where a guard or a loop that may not run matters).
- bulk: `add_parameter` of `more` on `Signatures.addParameter`; one batch of 11 steps (two
  `add_parameter`, three `replace_declaration`, one `add_declaration`, five `replace_text`),
  about 230 lines; then a batch of two `replace_text` (refusal wording) and `remove_parameter` of
  `Introduce.parameter`'s unused `line`.
- refusal: the first batch's step 10 (`replace_text` expecting a laid-out call); resent whole,
  since the CLI forgets the refused batch (friction log).
- plan: goldens (9) and probes (8) written before the code; the first run failed only on two
  `result.txt` files with a trailing newline.
- hand: none in `src/main`. Golden and probe files with file tools, as the brief says.

## Measured

Not measured: the session-log hook is not loaded here. From the notes: 5 tool calls into
`src/main` (1 `add_parameter`, 2 batches of 13 steps, 1 `remove_parameter`, 1 batch of 2 in
`Tools`), 4 batches or calls for docs; 9 golden cases and 8 probes with file tools.

## Assessment

Through the tools. The plan held; the hook kept the extraction one change, which also removed the
re-find by text. The probes found the pre-existing re-read change in the form without a call
(`run(step, tick())`), which the type check could not see.

## Follow-ups

- A call on the method's own object, or a static method named without its class, is refused;
  the options (write it on each call's receiver; qualify a static method at a call in another
  type) are in `docs/design.md`, Decisions.
- A constant or a static method's class named in the expression is written as is at each call;
  a call in another class with its own name for it would read that one. `NameCheck` of the
  written arguments would catch it (both forms; the gate catches only a name that does not
  resolve).
- `all_occurrences` with `as=constant` or `field`, and for a local, is refused.
