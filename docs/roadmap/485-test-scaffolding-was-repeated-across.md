---
order: 475
item: 485
status: Done
---

## Item

Test scaffolding was repeated across test classes: `ServerTest` copied a tree by hand beside `GoldenTest.copyTree`, `StubsTest` built the same Maven-layout project twice, and `TypeCheckTest` and `BatchRetryTest` each wrote files with their own `createDirectories` and `writeString` (issue #531, code review)

## Status

Done 2026-11-14: `TestProjects` in the test root: `write(root, rel, text)` (directories made, the file returned), `maven(root, editing)` (both source roots made, the session editing or not) and `copyTree` (moved from `GoldenTest`: delegate, then `inline`); the four cited classes and `ProbeTest` call them; `TestProjectsTest`. The three missing tests the review named exist already (goldens `replace_text_refused_empty_old`, `batch_refused_client_undo`, `JournalTest.deletingTheOnlyFileKeepsTheProjectRoot`, from #462, #473, #479). Left: the other `createDirectories` plus `writeString` pairs in about 20 test classes, moved to `write` when a test class is next touched

## Evidence

Issue #531; `docs/sessions/test-projects.md`
