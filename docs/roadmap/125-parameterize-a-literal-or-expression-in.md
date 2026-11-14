---
order: 118
item: 125
status: Done
---

## Item

`parameterize`: a literal or expression in a method's body becomes a parameter that every call passes (beyond `extract_variable` `as=parameter`'s literals and constants)

## Status

Done 2026-11-06 (the user: widen `extract_variable` `as=parameter`, over a new tool): the method's parameters, each call passing its own arguments

## Evidence

gorefactor `docs/parity.md` section 3; no Java friction entry yet
