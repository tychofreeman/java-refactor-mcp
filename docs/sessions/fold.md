# fold (roadmap #103)

- **Item:** roadmap #103, from the A/B rerun: `warehouse-dedupe` cost the mcp arm
  $0.15 against $0.09, all of it reading and composing ten replacements by hand. The
  user's "Yes" of 2026-11-05.
- **Date:** 2026-11-05
- **Branch:** fold

## Decisions (this session)

- **A new tool, gorefactor's contract** (`fold`, `file`, `name`, `line`, `in`): the
  shared contract asks for the same tool in both; no existing Java tool's option fits
  (it is inline's inverse, across the project).
- **First stage:** static methods; a one-return body matches expressions, a void
  body runs of statements. Parameters are holes; a body's locals map one-to-one to
  the match's; every other name compares by binding key (the files are parsed
  together by `Holding`, by a name of the body as anchor). Instance methods are
  left for evidence.
- **Safety by the gate:** each argument is a `Behavior.Move` to the call (its
  landing the start of the match), with the arguments before it moving along
  (`Move.along`, new: they keep their order, so they are not crossed). A match the
  gate refuses is left, with the reason, not the fold refused: the result lists
  what was folded and what was left.
- **Order and number:** arguments used in another order than the parameters are
  left only when both are not fixed and one has an effect (a local read before an
  effect does not change). A parameter used twice takes an argument that is fixed,
  or one without effects that nothing in the body between its uses could change:
  the record component `limit` read twice in the warehouse sensors (first refused
  all nine; the rule was too strict).
- **The format relaxation** (gorefactor's "a %s of a format matches constant
  text"): for `String.format` on both sides, a plain `%s` whose argument is a
  parameter is either a `%s` with its argument in the code, or constant text; the
  parameter is then given that text as a literal made for the call (it has no
  source position; `made` marks it, and the gate and `NameCheck` skip it).
- The A/B's per-arm instructions now say to merge near-duplicates by writing the
  shared method and folding it.

## Notes

- The first four goldens passed on the first run; the format golden failed until
  a hole's `%s` could also stay a `%s` (the sensors' `room` is a parameter too).
- My first `fold_left_with_reasons` expected a fixed local and an effect reordered
  to be left; it is safe, and the golden folds it.
- `bulk:` `Fold` is 600 lines of new code, by `create_file` and `add_declaration`.
- `hand:` none in Java.

## Measured

All Java through jrefactor: `create_file`, `add_declaration`, `replace_text`,
`replace_declaration`; goldens by `create_file`.

## Follow-ups

- Instance methods (the receiver as a hole); string concatenation relaxed like a
  format (gorefactor matches part of a `+`); a stress run on a real project.
- Rerun the A/B set to measure #102 and #103.
