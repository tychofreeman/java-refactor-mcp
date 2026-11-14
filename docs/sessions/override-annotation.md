# One @Override detector

- **Item:** issue #506 (code review of 2026-11-13, moves #17), group 4 of tracking issue #532
- **Date:** 2026-11-14
- **Branch:** override-annotation

## Plan

Reproduce both mismatches: `add_result` on a method marked with a project annotation whose name
ends in `Override` (refused today), and `move` of a method marked `@java.lang.Override` (not
refused as an override today). Then one `Nodes.overrideAnnotation(BodyDeclaration)`: the
annotation's resolved type when it resolves, else its name; the five sites call it.

## Notes

- read: `inspect` with `pattern` `Override"` (five sites: the issue's four and `AddResult`),
  then each range.
- plan: both goldens failed: `add_result` refused "overrides a method"; `move` failed the gate
  ("Fees has no supertype declaring toString(Price)") instead of the override refusal.
- tools: a batch of six steps, a `replace_text` fixing the unresolved case (a recovered binding),
  three `inline`s, a batch of two `replace_text`s, `add_declaration` for the `NodesTest` case.
- refusal: `replace_text` with `content_file`: its argument is `new`, so `new_file`.
- hand: goldens, docs.
- default chosen: a resolved annotation counts only as `java.lang.Override`; an unresolved one by
  its name, `Override` or `java.lang.Override`. The issue's binding check where one exists.

## Measured

Not measured: the session-log hook is not loaded here (the CLI log is).

## Assessment

Through the tools. Delegate-then-inline again needed a tidy of the inlined expressions.

## Follow-ups

- `move` and `push_down` judge an override by its annotation first; an override without one is
  `Hierarchy`'s (#495).
