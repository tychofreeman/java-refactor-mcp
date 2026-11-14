---
order: 70
item: 77
status: Done
---

## Item

A check that every name in moved text resolves to the same declaration after the edit, for every tool that moves code; it would replace one-off rules such as `inline_function`'s other-class member refusal and `inline_variable`'s shadow refusal

## Status

Done (2026-11-04), local session: `NameCheck` in `inline_function`, `inline_variable` and `convert_lambda`; `pull_up`, `push_down` and `move_member` too (second PR); `extract_method` and `move_type` too (`docs/sessions/same-meaning-extract-move.md`); the one-off rules stay for their clearer refusals; record in `docs/sessions/same-meaning-check.md`

## Evidence

Follow-up of #67 and #68 (`docs/sessions/effect-order.md`); the general form named in row 60's evidence
