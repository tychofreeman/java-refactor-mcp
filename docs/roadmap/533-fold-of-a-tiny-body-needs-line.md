---
order: 523
item: 533
status: Done
---

## Item

`fold` of a method whose body is a literal or one operator on its parameters matches that shape anywhere: without `line` it folded 13 unrelated `""` across 8 files. Such a body's result warns that it matches code that only looks like it.

## Status

Done (2026-11-20, issue #697): the owner chose to keep folding everywhere without `line` and warn in the result (`Fold.tiny`; `docs/design.md`, Decisions).

## Evidence

gorefactor's TypeScript issue #714, checked against Java on 2026-11-20 (worse here: `fold` without `line` searches the whole project).
