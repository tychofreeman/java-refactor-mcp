# One rule set for a parameter expression's calls

- **Item:** issue #500 (code review, signatures #5), the owner's option (a) of 2026-11-14; roadmap #495
- **Date:** 2026-11-14
- **Branch:** one-parameter-rules

## Plan

One `checkCall(InMethod, Call, Direction)` in `ParameterValue` that both callers use. Before
it, probe each disagreement between the copies in both directions (type, the re-read `i > at`,
the other arguments' order), and let behavior decide the rule. Operations: `add_declaration` of
the method, `replace_statements` of each copy with a call of it, `inline` of what is left
(`passed`), and `erasedKey` made a delegate of `declarationKey` and inlined.

## Notes

- read: `ParameterValue` outline, `passed` and `cast`, `Signatures.removeWithExpression`'s
  per-call loops, `Bindings`, `Effects.fixed`, `Introduce.parameter`'s placement.
- plan: the type rule agrees on when a conversion is involved; only the cast differs, by #171's
  decision. `i > at` cannot arise for `extract_variable` (#172 places the parameter after every
  read one). The real difference: `remove_parameter` refused any argument with effects between,
  `extract_variable` only one that could change the re-read argument.
- red: probe `remove_parameter_value_argument_between_locals` refused ("runs tick() between the
  arguments"); four pins passed before.
- bulk: one batch, `add_declaration` of `Direction` and `checkCall`, two `replace_statements` in
  `Signatures`; then `replace_statements` of `passed`'s loop and `inline` of `passed`.
- refusal: `inline` of the locals that inlining `passed` made for fields (a call between could
  change a field); `replace_statements` instead.
- refusal: `inline` of `Bindings.erasedKey` (a qualified use counted as a member of the class);
  `move` to `ParameterValue`, then `inline`, in one batch. Friction log.
- `replace_expression`: the "passes X, not V" refusal wrote the argument by `toString()` (`(U)x`);
  now its source.

## Measured

Not measured (no session-log hook loaded). From the notes: 11 tool calls that wrote Java, none
by hand; resource files (probes, goldens) and docs with ordinary file tools.

## Assessment

The tools carried every Java change; the two refusals each cost one call and named a way round.

## Follow-ups

- `InlineFunction.usesMembersOf` should skip qualified names (`Nodes.qualified`).
