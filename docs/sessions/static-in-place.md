# Instance method to static and back, in place (roadmap #90)

- **Item:** roadmap #90 (gorefactor's `method_to_function`, `function_to_method`).
  The user's list of 2026-11-04.
- **Date:** 2026-11-04
- **Branch:** static-in-place

## Decisions (this session)

- **Instance to static is an option, not a tool:** `move_member` already makes a
  moved method static with a `receiver` parameter; with `to` the method's own
  class it does that in place. It refused that target; the refusal was the only
  change needed.
- **Static to instance is a tool, `function_to_method`:** a static method whose
  first parameter is of its own class; the parameter becomes the receiver.
- **A null first argument** failed where the body used it (or never); as a
  receiver it fails at the call. Reported as a change to exceptional behavior,
  refused under `preserve_exceptions`.

## Plan

1. Red: goldens `move_member_to_static_in_place` (not run red: it was refused
   before the change, by the precondition removed), `function_to_method` (with
   `exceptional.txt`; red as an unknown tool).
2. Green: the precondition by `replace_text`; `FunctionToMethod` by
   `create_file`; its `Def` by `add_declaration`.

## Notes

- I named a local `self_`; `rename` made it `onThis` (the first `rename` gave a
  line from before formatting and was refused: "'self_' is not on line 118";
  sent again with 127).
- Both goldens passed on the first run after the change.

## Measured

All Java through jrefactor (`create_file`, `add_declaration`, `replace_text`,
`rename`), no refusals.

## Assessment

- **Through the tools or around them:** through.

## Follow-ups

- `move_member` in place writes `Price.charge(this, a)`; `charge(this, a)` would
  read better inside the class.
