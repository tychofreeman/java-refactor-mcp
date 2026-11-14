---
order: 99
item: 106
status: Done
---

## Item

`replace_text` when `old` is not in the file: name the nearest lines, and accept an `old` that differs only in indentation when it then matches once, as gorefactor's does

## Status

Done 2026-11-06 as a uniform shift; replaced the same day by gorefactor's rule (any run of spaces within lines for any other), `new` moved to the file's indentation (the user's choices, `docs/sessions/replace-text-go-spacing.md`); the nearest lines named on a miss

## Evidence

Friction triage: two mismatches on 2026-11-02, both indentation copied from `inspect`; the session notes' traps record the same mistake since
