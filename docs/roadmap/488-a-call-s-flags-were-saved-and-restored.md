---
order: 478
item: 488
status: Done
---

## Item

A call's flags were saved and restored by hand in `Tools.call` (`editing`, `preserveExceptions`, `exceptional`) and `Gate.deferred` (`deferred`), and `editing` stayed true after a top-level edit until the next call; `inline` and `fold` tried `Behavior.flow` tentatively by noting the size of the session's report list and truncating it on a refusal (issue #525, code review)

## Status

Done 2026-11-14: `Session.scope(edit, preserve)` opens a call's `Scope` (its flags and its own report list; `keep()` passes the reports to the outer call, `close()` puts back the outer flags, a step that edited leaving its batch editing, nothing editing after a top-level call); `Session.deferring()` returns a `Restore`; `Exceptional.trial(project, body)` returns the refusal or null and drops the reports a refused trial made, used by `InlineFunction` and `Fold`. `ToolsCallTest.anEditLeavesNoFlagSetAfterIt` (failed before), `SessionTest`

## Evidence

Issue #525; `docs/sessions/session-scope.md`
