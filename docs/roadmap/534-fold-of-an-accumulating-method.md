---
order: 524
item: 534
status: Done
---

## Item

`fold` of a method that assigns a parameter and returns it (`rpn += text; return rpn;`, as `extract_function` writes for a statement that updates a local) matches a statement that updates a variable the same way, writing `rpn = press(rpn, more.trim());`.

## Status

Done (2026-11-20, issue #699): `Fold.Ending.ACCUMULATE`.

## Evidence

gorefactor's TypeScript issue #727, checked against Java on 2026-11-20: the second copy of an extracted accumulating statement is refused ("assigns its parameter rpn, which no expression can stand for") and edited by hand.
