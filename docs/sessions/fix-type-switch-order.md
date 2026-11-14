# type_switch_to_method on an if chain ignores case order

- **Item:** issue #467 (code review of 2026-11-13, control-flow #1), roadmap #215
- **Date:** 2026-11-13
- **Branch:** fix-type-switch-order

## Plan

In an `if` chain the first test that matches wins; after the rewrite the most specific override
runs. Refuse, in `TypeSwitch.chain`, a case whose type is a subtype of an earlier case's type.
Probe first (a `Square extends Polygon` after `Polygon`), then a golden for the refusal text, then
the check: one loop over the cases read so far, `isSubTypeCompatible` on the erasures `typed`
already gives, all from one parse.

## Notes

- read: `inspect` of `TypeSwitch` without bodies, then lines 63-455 (the review named `chain`).
- plan: probe `type_switch_chain_order` printed "square" for a Square after the rewrite (was
  "poly"); golden `type_switch_to_method_refused_order` saw nothing thrown. Both red.
- `replace_text` (a batch file with `@@old`/`@@new` sections) wrote the check; it moved `new` 2
  spaces shallower to the file's indentation, as it says. A second `replace_text` reworded it.
- Docs (contract, roadmap row, this record) by hand: they are not Java.

## Measured

Not measured: the session-log hook is not loaded in this session. Two tool calls to `src/main`, no
hand edit of Java.

## Assessment

Through the tools. The plan held; the fix is one loop. Only the subtype-after-supertype order
changes which body runs; the reverse order dispatches as the chain did.

## Follow-ups

- Two unrelated interfaces both implemented by one class could also order differently; the gate
  refuses that today, since a case's type that is an interface gets a non-default method.
