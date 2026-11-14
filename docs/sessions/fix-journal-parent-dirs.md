# Journal.put: stop removing directories at a source root and the project's root

- **Item:** issue #479 (code review of 2026-11-13), roadmap #234
- **Date:** 2026-11-13
- **Branch:** fix-journal-parent-dirs

## Plan

Make the change easy first: `Journal.put` and `restore` take the project (`add_parameter`, value
`project` at each call, one batch with check "end"). Then two red tests in `JournalTest` (the
project's root, an empty source root) and a `removable` check in `put`'s upward walk.

## Notes

- refusal: `add_parameter` does not take `position`; `before` names the parameter. Fixed with
  `retry`, three `step` changes.
- plan: the tests named in `add_parameter`'s value had no `project`; two `replace_text`s in the
  same batch declared one.
- plan: both tests red (the root and the source root were deleted), green after `removable`.
- plan: the smallest of three options (Decisions, design.md); recording created directories was
  not taken.

## Measured

Not measured: the session-log hook is not loaded here. Three batches and one `add_declaration`
(the method replacing its stub); no Java by hand.

## Assessment

Through the tools. The refactor-first step was one batch.

## Follow-ups

- Record the directories a call creates, so one the user made empty elsewhere stays (option b).
