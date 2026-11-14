---
order: 106
item: 113
status: Done
---

## Item

A class stub declared from a test file goes in the main source root, in the test's package: today it lands beside the test (`src/test/java`), and no tool moves Java between source roots

## Status

Done 2026-11-06: the main root, test doubles and helpers excepted (the user's choice over always main and over a move between roots)

## Evidence

Friction log 2026-11-05 (a probe of creating a file test first): `create_file` of a test using an undeclared `Cart` put `Cart.java` in `src/test/java`; `move_file` and `move_type` cannot move it to `src/main/java`; creating the empty class first works
