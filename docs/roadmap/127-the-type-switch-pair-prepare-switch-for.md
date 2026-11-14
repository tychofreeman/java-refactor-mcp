---
order: 120
item: 127
status: Done
---

## Item

The type switch pair: `prepare_switch_for_types` and `type_switch_to_method` (an `instanceof` chain, or a switch over a sealed type, becomes a method on each type)

## Status

Done 2026-11-06 (the user: one tool, the prepare step inside it): `type_switch_to_method` over a switch expression or statement of type patterns, a default, or an instanceof chain

## Evidence

gorefactor `docs/parity.md` section 3
