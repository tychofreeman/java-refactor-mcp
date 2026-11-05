# move_statement onto the behavior gate (roadmap #96)

- **Item:** #96's third user.
- **Date:** 2026-11-05
- **Branch:** gate-move-statement

## Decisions (this session)

- **A swap is one move:** the later statement moves before the earlier one, which
  is what it crosses. The gate's rules are symmetric enough: an effect crossing an
  effect, a write crossing a read of its variable, a call crossing a read of a
  field, and what may throw on either side of a change.
- **The tool keeps two rules of its own,** the shared name and the two side
  effects, since their refusals name `names_only`; `names_only` is the move's
  `commute`, which lets two effects (and a call past state the other reads)
  cross.
- **Early exits are the gate's:** `Effects.leaves` (moved from `MoveStatement`)
  marks a statement that may return, or break, continue or yield out of itself.
  An effect crossing one is refused; code that may throw lifted above one is
  refused (the guard is kept, by the policy), reversing #167's report
  (`move_statement_refused_guard_may_throw`); moved below one, it is reported.
- **Probe, before the change:** `int y = count; bump();` swapped to
  `bump(); int y = count;` applied (one side effect, no shared name) and changed
  `y`. Now refused (`move_statement_refused_call_past_read`).
- The two exceptional reports changed to the gate's wording.

## Notes

- `refusal:` an `add_declaration` naming `Statement` without `imports`; resent.
- `hand:` none. `MoveStatement` 313 to 168 lines.

## Measured

All Java through jrefactor: `add_declaration` (two), `replace_text`,
`replace_declaration` (four safe deletes); goldens by `move_file` and
`create_file`. One refusal (imports).

## Follow-ups

- `inline_function` onto the gate.
