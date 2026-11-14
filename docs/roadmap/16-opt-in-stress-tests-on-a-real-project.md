---
order: 14
item: 16
status: Partly done
---

## Item

Opt-in stress tests on a real project for tools whose rules are heuristics, with a counts-only mode

## Status

Partly done; behavior probes run with every build since 2026-11-05 (`ProbeTest`, 22 cases: a program run before and after a tool; `docs/sessions/probe-test.md`)

## Evidence

From gorefactor; compare versions on identical input; `NarrowCheckStressTest` for the narrowed type check (`-Djrefactor.stress.root`, `.edits`, `.seed`, `.counts=true` for counts only), record in `docs/sessions/stress-narrow-check.md`; `RefactoringStressTest` calls the refactorings on random targets (applies or refuses, adds no error by a full count, undo restores every file): no failures in 160 calls on Commons Lang and Math, three refusals by the gate that are tools' missing preconditions (`docs/sessions/stress-refactorings.md`); the five newer refactorings added 2026-11-04, no failures in 220 calls, behavior changes found by probes instead (#67-#70, `docs/sessions/stress-new-tools.md`)
