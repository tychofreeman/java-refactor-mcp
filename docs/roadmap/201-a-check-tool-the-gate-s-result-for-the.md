---
order: 194
item: 201
status: Done
---

## Item

A `check` tool: the gate's result for the project, and with `run_tests` the Maven tests, failures by test and line; `check` as a batch step, a checkpoint that keeps the steps before it when a later one is refused

## Status

Done (2026-11-14): `Check`. Which tests `run_tests` runs is the user's (#367, and 2026-11-15: the tests whose code uses a changed main file, directly; `tests` names others, and `timeout`, 30 s by default, stops the run), with the options it beat in `docs/design.md`, Decisions

## Evidence

Issue #367 (parity review): it changes how every session works, red and green through the tools
