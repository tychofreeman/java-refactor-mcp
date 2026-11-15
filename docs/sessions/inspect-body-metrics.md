# inspect: complexity and nesting in the outline

- **Item:** issue #369, third part; roadmap #506
- **Date:** 2026-11-15
- **Branch:** inspect-body-metrics

## Plan

`Inspect.bodies` returns a record per body (elided or not) with its facts; `outline` writes them.
The metric is gorefactor's `Metrics` with Java's branch points. Side effects need a choice Java
forces (how far into calls, which library calls are pure, a binding parse in the file view), so
they are left for the owner.

## Notes

- plan: the two outline goldens rewritten to the new brackets, and `inspect_outline_metrics`; red.
- bulk: one batch of five steps (`add_declaration` x2, `replace_declaration` x2, `replace_text`).
- hand: the golden `result.txt` edits by script (resources, not Java); one duplicated a line, fixed.
- refusal: none.

## Measured

Not measured (no session-log hook). Java changes: 3 tool calls (the batch, a `rename`, a batch
of two in `Tools`), none by hand.

## Assessment

Through the tools.

## Follow-ups

- Side effects in the brackets: the owner's choice (issue #369).
