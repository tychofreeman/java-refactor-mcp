---
order: 519
item: 529
status: Open
---

## Item

`fold` of a method whose body is statements ending in a return, as gorefactor's roadmap 67 (its #694, #695, and TypeScript's #698): a run of statements ending in the same return becomes `return f(args);`, early returns included, where the enclosing method's return type takes `f`'s; a copy that goes on after the statements before `return x` becomes `T x = f(args);` (TypeScript's form; Go's error form has no Java counterpart). Today `fold` takes a method that is one return, or a void method's statements.

## Status

Open (2026-11-19)

## Evidence

The design-routine A/B (`docs/sessions/ab-design-routine.md`): in 5 of 6 `shop-duplication` runs, both arms, `fold` of the extracted filter-and-sort method was refused ("returns a value from statements: fold matches a method that is one return, or a void method's statements. Extract the expression into one first."), and all six runs replaced the other two copies with `replace_statements`, a call typed by hand that nothing checked was the same code. gorefactor found the same: 27 of 37 edit steps of its #691-#693 were text edits replacing a copy with a call.
