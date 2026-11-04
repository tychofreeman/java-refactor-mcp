# inline_function: an effect and a read in another order (roadmap #67, follow-up)

- **Item:** the follow-up of `docs/sessions/inline-function-effect-gaps.md`:
  an argument with an effect and another that reads a field, used by the body
  in the other order than the call passes them. `main` refuses two arguments
  with effects (`docs/sessions/effect-order.md`), not one effect and one
  read. Under the policy in `docs/design.md` (effects in normal flow, reads
  included, keep their order), it must be refused. The user asked for the
  uncovered cases (2026-11-04).
- **Date:** 2026-11-04
- **Branch:** inline-function-read-order

## Plan

The change: arguments that are not fixed, at least one of them with an
effect, are evaluated by the inlined body in the order the call passed them.

1. Red: golden `inline_function_refused_read_order`: `f(Counter.bump(),
   Counter.count)` with `return b + a;`, by `create_file`.
2. Green: `add_declaration` of `checkOrder` (each such argument's first use,
   in argument order; refused where an argument with an effect and another
   swap), and a `replace_text` calling it after `checkTiming`.

## Notes

- Golden red first (applied), green after `checkOrder`.
- I wrote `java.util.Arrays.fill` in the new method; a second
  `replace_text` made it `Arrays.fill`, and the import came with it.
- No `read:`: `checkTiming`, written in the previous item, was the model.

## Measured

From `bin/session-report` (before the docs edits):

- tool calls: 11
- through jrefactor: 10 edit steps (5 `create_file`, 4 `replace_text`,
  1 `add_declaration`)
- refused by jrefactor: 0
- by hand: 1 (the record), then the docs by a heredoc and Python
- large writes: 1 (`checkOrder`, new)

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes.
- **One change that would have helped:** none.

## Follow-ups

- `{}` on one line in `add_declaration`, `move_member` and
  `encapsulate_field`: the next item.
