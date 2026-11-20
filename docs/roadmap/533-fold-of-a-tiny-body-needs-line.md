---
order: 523
item: 533
status: Open
---

## Item

`fold` of a method whose body is a literal or one operator on its parameters matches that shape anywhere: without `line` it folded 13 unrelated `""` across 8 files. Such a body takes `line`, and folds only the match there.

## Status

Open (issue #697, filed 2026-11-20)

## Evidence

gorefactor's TypeScript issue #714, checked against Java on 2026-11-20 (worse here: `fold` without `line` searches the whole project).
