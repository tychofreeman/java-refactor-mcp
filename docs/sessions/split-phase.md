# split: a method into phases

- **Item:** issue #370, roadmap #205 (gorefactor's `split` kind `phase`)
- **Date:** 2026-11-15
- **Branch:** split-phase

## Plan

Name what the result is made of: extract the tail (`extract_function`), group the values the head
computed into a parameter object (`extract_type` of the new method's parameters). Tried by hand
with the CLI on a scratch project first: `extract_type` refused the two values while a method
parameter sat between them ("not next to each other ... move_parameter"), and passed after one
`move_parameter`. So the tool is those three calls, the moves computed from the new signature.

## Notes

- read: gorefactor's `internal/refactorings/split/split.go` (kinds, arguments, the phase's two steps).
- plan: goldens `split_phase` and three refusals, probe `split_phase`; red.
- bulk: `create_file` of `Split.java` (178 lines); one batch of two `replace_text` in `Tools`.
- refusal (test): a refusal fragment that did not match the wording; the fragment fixed.
- hand: none in Java.
- found: roadmap rows 200, 202 and 205 already held #369, #368 and #370; the earlier PRs had
  added rows 502 and 504-506 beside them. Fixed here: 200 and 202 point to the new rows, 205 is
  this item's row.

## Measured

Not measured (no session-log hook). Java changes: 2 tool calls, none by hand.

## Assessment

Through the tools; the composition was found by running the tools, as CLAUDE.md asks before
deciding no tool covers a change.

## Follow-ups

- gorefactor's kinds `variable` and `loop`.
- gorefactor's `docs/parity.md` lists `split` as missing in Java: mark it there (its repo, its PR).
