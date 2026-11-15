---
order: 194
item: 201
status: Done
---

## Item

A `check` tool: the gate's result for the project, and with `run_tests` the Maven tests, failures by test and line; `check` as a batch step, a checkpoint that keeps the steps before it when a later one is refused

## Status

Done (2026-11-14): `Check`. Which tests `run_tests` runs is the user's (#367: those the changes reach); the rule that decides them is a default of this session, with the options it beat in `docs/design.md`, Decisions, to reopen

## Evidence

Issue #367 (parity review): it changes how every session works, red and green through the tools
