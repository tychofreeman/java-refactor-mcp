---
order: 65
item: 72
status: Done
---

## Item

One effects analysis: what an expression or statement reads, writes and calls, in order, shared by the tools that move code; it replaces `ExtractVariable.hasEffect`, `MoveStatement.Effects`, `InlineVariable.assigns`/`readsIn` and `InlineFunction`'s argument checks, and has room for what may throw

## Status

Done (2026-11-04), local session: `Effects`; record in `docs/sessions/one-effects-analysis.md`

## Evidence

Same review: four partial models of effects, none knowing exceptions. #73 needs a fifth unless they become one. No behavior change; the goldens and the effect-order probes are the check
