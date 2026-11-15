---
order: 195
item: 202
status: Done
---

## Item

`inline` of one call: `name` a method with `line` (and `column`) inlines the call there and keeps the method

## Status

Done 2026-11-15 as #502 (a row added beside this one by mistake): the user chose gorefactor's rule, what `name` is at `line` says which, with `all_calls` and `keep_function`

## Evidence

Issue #368 (parity review); moves callers over one at a time, as `fold` with `line` does the other way
