---
order: 127
item: 134
status: Done
---

## Item

`convert_loop` from `do_while` to `while`: `while (true) { body; if (!cond) break; }` or the body copied before the loop

## Status

Done 2026-11-11 (`ConvertLoop.doToWhile`): `while (true)` and `break`, the user's choice of 2026-11-11 over copying the body before the loop and over both behind an option; a `continue` of the loop is refused

## Evidence

`docs/sessions/convert-loop-sketch.md`, question 3
