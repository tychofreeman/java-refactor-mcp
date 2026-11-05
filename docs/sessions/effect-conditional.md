# Where code runs: later, or only sometimes (roadmap #76)

- **Item:** roadmap #76: one place that says whether code runs only sometimes,
  replacing the parent walks in `ExtractVariable.checkPlace` and
  `InlineFunction.deferral`. The user's order of 2026-11-04 ("then #76").
- **Date:** 2026-11-05
- **Branch:** effect-conditional

## Decisions (this session)

- **A query, not a field.** The row asked for a `conditional` mark on `Effect`.
  Both callers ask about a node that is not an effect (a parameter's use, an
  expression to extract), relative to a root they choose (the body, the
  statement). So it is two static queries on `Effects`: `later(node, root)` (a
  lambda, an anonymous class) and `sometimes(node, root)` (a branch of `?:`,
  right of `&&`/`||`, a branch of an `if`, a loop's condition, update or body, a
  `switch` case, a `catch`), each giving the words for a refusal. A field would
  have had no reader.
- **The statement-level branches are new.** `deferral` walked only expressions,
  so `inline_function` put an argument with an effect into an `if` branch
  (`g(b, next())` became `if (b) { use(next()); }`) or an enhanced `for` body
  (`next()` once per element). Both applied and type-checked; found by a probe
  while planning this item. Now refused.
- **`extract_variable` refuses by position, not by ancestor:** the condition of
  a `?:` and the left operand of `&&`/`||` run whenever the statement does, so
  they may be extracted now; before, anything under a `?:` or `&&` was refused.

## Plan

1. Red: goldens `inline_function_refused_in_if`, `inline_function_refused_in_loop`.
2. Green: `add_declaration` of `later` and `sometimes` in `Effects`;
   `replace_declaration` of `deferral`, then of `checkPlace` (its loop checks go
   into `sometimes`); safe delete of `updates`; the descriptions.
3. `extract_variable_conditional_condition` for the widening.

## Notes

- `read:` `deferral` and its two callers, `checkPlace`, to see what each walk
  covered.
- Both red goldens applied before the change (no refusal), as the probe did.
- The goldens pinning the old wording (`in a branch`, `inside a lambda`, `loop
  condition`) still match.

## Measured

All Java through jrefactor (`add_declaration`, `replace_declaration`, a safe
delete, `replace_text` of descriptions, `create_file` of goldens), no refusals.

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes, once the field became a query.

## Follow-ups

- `inline_variable` and `move_statement` have their own notion of "inside a
  guard, loop, lambda or try" (`docs/sessions/exceptional-more.md`); move them
  onto these queries when either is next changed (#96 would subsume it).
