# A call's flags in one scope (#525)

- **Item:** issue #525, from the code review of 2026-11-13 (group 5 of #532): session state saved
  and restored by hand, `editing` left set after a call, and tools truncating the session's list
  of exceptional reports to roll back a tentative check.
- **Date:** 2026-11-14
- **Branch:** session-scope

## Plan

`Session.Scope` (AutoCloseable) snapshots and restores the per-call flags, used by `Tools.call`;
`Session.deferring()` for `Gate.deferred`; `Exceptional.trial(project, body)` for `InlineFunction`
and `Fold`. Red first: after a top-level edit, `session.editing` must be false.

## Notes

- read: `Tools.call`, `Gate.deferred`, `Session`, `Exceptional`, `InlineFunction`'s argument loop,
  `Fold`'s candidate loop; PR #599 (node-text) also edits `Fold` and `InlineFunction`, in other hunks.
- red: `ToolsCallTest.anEditLeavesNoFlagSetAfterIt` failed (editing stayed true).
- refusal: `Fold`'s trial named its refusal `left`, a local already there ("Duplicate local
  variable left"); the retry renamed it.
- hand: none. `-Xlint:try` fails a try-with-resources whose resource the body never names
  (`Gate.deferred`): written as try/finally; the tests carry `@SuppressWarnings("try")`.
- Behavior kept: a refused tool call drops its reports, a batch step's kept reports join the
  batch's, a step that edited leaves its batch editing for the end check.

## Measured

Not measured (no session-log hook); `JREFACTOR_LOG` holds the CLI calls.

## Assessment

Through the tools (three batches, `add_declaration`, `create_file`).

## Follow-ups

- None.
