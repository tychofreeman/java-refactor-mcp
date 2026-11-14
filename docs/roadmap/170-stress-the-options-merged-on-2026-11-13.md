---
order: 163
item: 170
status: Done
---

## Item

Stress the options merged on 2026-11-13 (`add_parameter` with `type_parameter` and `members`, on a method, on an enum with `values`; `change_type` bounds and wildcards; `remove_parameter` with an expression `value`; `extract_variable` `as=parameter` with `all_occurrences` and a call; `extract_supertype` from a generic class; `type_switch_to_method` over an enum) on Commons Lang, and `NarrowCheckStressTest` once

## Status

Done 2026-11-13 (`RefactoringStressTest`: ten new entries, `jrefactor.stress.tools`, `jrefactor.stress.refusals`; 550 calls and 100 edits: 5 failures, issues #405-#411; the enum switch forms got no target, Lang being Java 8)

## Evidence

The owner, 2026-11-13; `docs/sessions/stress-new-options.md`
