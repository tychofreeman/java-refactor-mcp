---
order: 94
item: 101
status: Done
---

## Item

`extract_variable`'s column refusal names the columns where the expression starts on the line

## Status

Done (2026-11-05), local session: `extract_variable` (a wrong column, and two occurrences) and `inline_variable` (a wrong column, which said "no variable named", and two variables) name the columns, by `Nodes.columns`; `rename` and `convert_lambda` already did. Goldens `extract_variable_refused_wrong_column`, `inline_variable_refused_wrong_column`

## Evidence

A/B run: three wrong columns guessed in two runs (`p.bin()` at 14 and 18, it was 16), a turn each
