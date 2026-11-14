---
order: 112
item: 119
status: Done
---

## Item

`replace_text`: an exact `old` that starts with spaces and matches inside a line's indentation (8 spaces inside 10) is taken only from a line's start, so `new` does not land at the wrong indentation; otherwise the #106 shift applies

## Status

Done 2026-11-06: such a match is shifted, or refused naming its lines

## Evidence

gorefactor `docs/parity.md` section 1, the first Java bug; friction log 2026-11-05 ("replace_text matched old inside a line's indentation"): three calls to put a text block back
