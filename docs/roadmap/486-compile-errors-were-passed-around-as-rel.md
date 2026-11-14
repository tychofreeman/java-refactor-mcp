---
order: 476
item: 486
status: Done
---

## Item

Compile errors were passed around as `"rel | message"` strings: `TypeCheck` built them, `narrowed` and `Stubs.files` cut the path back out at the first `" | "`, and `introduced` appended `" (xN)"`; a file whose path held `" | "` was never counted again, so its old errors stayed in the cached count after it was fixed (issue #523, code review)

## Status

Done 2026-11-14: `TypeCheck.Problem(rel, message)` is the count's key and `TypeCheck.Added(problem, extra)` what `introduced` returns; `Gate.refusal` alone renders them as text (unchanged wording), and its hints match the message only. `TypeCheck.FORMAT` 5 saves counts as objects (`file`, `message`, `count`). `TypeCheckTest.aPathWithTheSeparatorIsCountedAgain` (failed before) and `introducedNamesTheProblemAndHowManyMore`

## Evidence

Issue #523; `docs/sessions/problem-record.md`
