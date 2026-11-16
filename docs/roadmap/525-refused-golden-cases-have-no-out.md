---
order: 515
item: 525
status: Done
---

## Item

A golden case that expects a refusal has no `out/` directory, which `GoldenTest` never reads for it

## Status

Done (2026-11-16): the 23 such `out/` directories are deleted, and `GoldenTest` fails a refused
case that has one. Each was a copy of its `in/`, so none hid a wrong result; they only made a
case look as if it checked an output. (Item 524 is taken by the refused-probes PR opened the same
day, so this one is 525.)

## Evidence

A review of the tests (the owner, 2026-11-16) found 23 cases with both `refused.txt` and `out/`.
