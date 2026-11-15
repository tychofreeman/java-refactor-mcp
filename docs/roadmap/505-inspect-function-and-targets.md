---
order: 495
item: 505
status: Done
---

## Item

`inspect` with `function` (one method of a file by name) and `targets` (several views in one
call), as gorefactor's (issue #369, second part).

## Status

Done 2026-11-15: `Inspect.function` (through `Inspect.names` limited to the file) and the
`targets` loop in `Tools.inspect`. Goldens `inspect_function`, `inspect_function_bare`,
`inspect_function_refused_missing`, `inspect_targets` (a refused target in its place),
`inspect_targets_refused_with_file`. The description was rewritten shorter to hold the limit.

## Evidence

Issue #369 and the parity review of 2026-11-13: gorefactor's recipes read a function by name and
several ranges in one call; here they were refused ("inspect does not take function").
