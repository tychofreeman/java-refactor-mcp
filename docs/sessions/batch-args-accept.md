# A batch step's arguments beside `tool`, read as its arguments

- **Item:** the second of the four items the user chose on 2026-11-13. The user chose to read
  such a step's arguments ("Accept them"), over refusing; another session had meanwhile built
  the refusal (#456, `docs/sessions/batch-args-beside-tool.md`), and the user, asked which
  should stand, said to change it to accept: design.md's option (b) and (c), reopened.
- **Date:** 2026-11-13
- **Branch:** batch-args-accept

## Plan

A step's arguments may come beside `tool` as well as under `args`; the batch reads them as one
set, and says in its result which steps it read that way.

1. Red: the golden case `batch_refused_args_beside_tool` becomes `batch_args_beside_tool`, with
   `out/` and a `result.txt`; a new `batch_refused_args_twice` for a key both beside `tool` and
   under `args` with different values.
2. Green: `Batch.argumentsUnderArgs` becomes `Batch.underArgs`, which returns the steps with
   the keys a tool takes moved under `args` (the refused batch kept for `retry` is in that shape)
   and the step numbers moved; the result adds one line naming them. A key in both places with
   different values is refused.

## Notes

- hand: the golden cases moved and written with git mv, cp and heredocs: no tool copies a case directory (as skeleton-build-blocks logged).
- read: `Batch.argumentsUnderArgs`, `takes`, `listed`, the call in `Batch.run`, and #456's golden
  case: to replace the refusal in place.
- bulk: `underArgs` (55 lines) by `replace_declaration`: new behavior, though its first half (the
  tool lookup, the keys beside `tool`) is `argumentsUnderArgs`'s loop typed again; editing that
  loop in place by `replace_statements` would have kept it, and writing the whole method was
  easier.
- hand: `result.txt` pinned from a run with a placeholder; the docs through a Python script.

## Measured

Not from `bin/session-report` (its log mixes this session's other branches); from the notes: one
`batch` of five steps and one `replace_text` through jrefactor, none refused; four `inspect`
reads; the golden cases and docs by hand.

## Assessment

- **Through the tools:** the Java went through one batch and one `replace_text`; nothing refused.
- **Large writes:** `underArgs` retyped the old loop's first half; `replace_statements` of the
  loop's tail would have moved less text.
- **Did the plan hold?** Yes, except that a key in both places with the same value is accepted,
  not refused.
- **One change that would have helped:** a way to copy a golden case directory (logged before).

## Follow-ups

- None for the item. The A/B instructions need no change: they never named the step shape.
