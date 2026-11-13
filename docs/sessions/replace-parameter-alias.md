# replace_parameter as an alias

- **Item:** roadmap #160 (order 153): the unpublished alias `replace_parameter` for
  `remove_parameter` with `value` (generators sketch, increment 8; this alias only, the
  `create_*` ones belong to another track).
- **Date:** 2026-11-13
- **Branch:** replace-parameter-alias (stacked on remove-parameter-value-call, #385)

## Plan

A `Def` beside `METHOD_TO_FUNCTION`, its own description and arguments with `value` required,
the handler `Signatures.removeParameter`; an entry in `Tools.ALIASES`. Goldens: the alias doing
#148's case, and the alias without `value` refused.

## Notes

- bulk: one batch of two `replace_text` steps in `Tools`.
- plan: `Arg.required()` marks the schema only; an alias is not published, so nothing checks
  it. Without `value` the alias ran a plain `remove_parameter`, refused for the body's read, not
  for the missing value. The handler calls `args.required("value")` first.
- hand: none.

## Measured

Not measured: the session-log hook is not loaded here. 3 `replace_text` calls (one a batch).

## Assessment

Through the tools. Handed over before CI to the next agent (the owner's choice, 2026-11-13).

## Follow-ups

- Item 4 of the track: `extract_variable` `as=parameter` with `all_occurrences` and with a call
  (roadmap #161).
