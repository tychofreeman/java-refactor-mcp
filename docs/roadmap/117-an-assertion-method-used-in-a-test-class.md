---
order: 110
item: 117
status: Done
---

## Item

An assertion method used in a test class that imports none (`assertEquals` in members added to a class made by `declaration`) is imported statically from the test framework, not declared as a stub

## Status

Done

## Evidence

A/B `shop-sonnet-skeleton` (2026-11-05): the stub `CurrencyExportTest.assertEquals(boolean, boolean)` was declared, then fought for six calls (`docs/sessions/ab-shop-tasks.md`)
