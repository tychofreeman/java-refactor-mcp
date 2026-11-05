# inline_function onto the behavior gate (roadmap #96, last part)

- **Item:** #96's fourth user, and the end of the row.
- **Date:** 2026-11-05
- **Branch:** gate-inline-function

## Decisions (this session)

- **Each argument is a move:** the code is the argument at the call, the root the
  body, the landings its parameter's uses. The gate learned code from outside its
  root (another tree, or not inside it): it ran just before the root, once, under
  no catch of the root's.
- **Deleted from `InlineFunction`:** `checkTiming`, `deferral`, the dropped-effect
  and used-twice refusals, and the "may throw, used only sometimes" report with its
  bookkeeping in `run`. Kept: the type rule, the two-effects rule and `checkOrder`
  (arguments against each other, which the gate does not see: the body's text has
  the parameter, not the other argument), and its own `NameCheck` over the body and
  every argument, so it calls `Behavior.flow`, the gate without names.
- **The gate checks the place first,** then what is crossed, then exits: for an
  argument with an effect used in a loop, "in a loop" says more than "another
  order".
- **Widened:** an argument that may throw, moved past an unrelated change in the
  body (`p.cost` past `total++`), is reported, not refused: the normal flow is
  the same. A call in the body before the use still refuses any argument that
  reads state.
- **#96 is done.** The other tools named in its evidence move whole statements,
  members or bodies to where they run at the same point in the flow; they need
  `NameCheck`, which they run, and keep their one-off rules.

## Notes

- `refusal:` a `rename` of `checkFlow` at a wrong line; resent with the line.
- Three golden fragments and one report changed to the gate's wording
  (`inline_function_refused_effect_before`, `_used_twice`, `_may_throw_unused`).
- `hand:` none. `InlineFunction` 1,118 to 1,025 lines.

## Measured

All Java through jrefactor: `rename`, `add_parameter`, `replace_text`, safe deletes.

## Follow-ups

- Rerun the A/B set (#11's follow-up) after #100 and #101.
