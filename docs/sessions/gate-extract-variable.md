# extract_variable onto the behavior gate (roadmap #96, #99)

- **Item:** #96's next user, `extract_variable`; it also does #99 (the first
  effect of a statement could not be named; every mcp run of the A/B's reorder
  task hit it).
- **Date:** 2026-11-05
- **Branch:** gate-extract-variable

## Decisions (this session)

- **Moves both ways.** `extract_variable` moves code earlier (before its
  statement): what it crosses is the part of the statement before it. The gate
  takes the direction from the positions.
- **Two gaps of the first gate (#173), found while planning this one:**
  - a moved effect crossing a *read* of what it changes was allowed
    (`int n = next(); int y = count; return n + y;` inlined would read `count`
    before `next()` changes it). Now: a write crossing a read of its variable,
    or a call crossing a read of a field or element, is refused.
  - a write later in a loop body changes the value at the next pass
    (`int v = x; for (...) { sum += v; x++; }`); the old `inline_variable` rule
    (any write in the block) caught it and #173's range stopped at the use. Now
    a landing in a loop crosses the rest of the loop.
- **The policy's guard and try rules are refusals** (`docs/design.md`: "a guard
  ... a try/catch: the source of an exception that is caught stays inside the
  try"). Code that may throw leaving a guard or a catching try is refused;
  entering one is reported. This reverses #167's report for an expression
  taken out of a try's resources (`extract_variable_refused_try_resource`); the
  same correction for `move_statement` (a dereference lifted above an
  early-return guard, reported by #167) comes with its move onto the gate.
- **Widened:** pure code from a `?:` branch or right of `&&` is extracted
  (`any ? price * qty : 0`, `extract_variable_conditional_branch`); the value is
  computed always, which changes nothing.
- **Wording:** the gate's refusals no longer say "declaration" or "Keep the
  variable", which fit only `inline_variable`; two golden fragments changed.

## Plan

1. Gate: direction, reads crossed by effects, loops, guard and try rules
   (`replace_declaration` of three methods, `Effects.loopOf`).
2. `ExtractVariable`: delete its own rules (`checkEarlierEffects`,
   `checkTryResources`, the effect and place refusals), declare the move after
   the edits.
3. Goldens: `extract_variable_first_effect` (#99),
   `extract_variable_refused_second_effect`, `inline_variable_refused_read_crossed`,
   `inline_variable_refused_loop_write`, `extract_variable_refused_guard`,
   `extract_variable_conditional_branch`.

## Notes

- `hand:` none. The gate stub `Exceptional.keep` was declared by the edit that
  used it; it became `throw new Refusal`, and the stub was deleted.
- `refusal:` none from the tools; two goldens of mine were wrong: a call where a
  dereference was meant, and columns 16/25 for `p.bin()` where they are 14/23
  (the A/B's mistake exactly; #101's evidence).
- Work started on `main` by mistake after merging #173; moved to the branch
  before the commit.

## Measured

All Java through jrefactor: `add_declaration`, `replace_declaration` (five,
three of them safe deletes), `remove_parameter`, `replace_text`; goldens by
`create_file` and `move_file`.

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** It grew by the two gaps, found by reasoning about the
  second user before writing it.

## Follow-ups

- `move_statement` and `inline_function` onto the gate; `move_statement`'s
  guard report becomes a refusal.
