---
order: 511
item: 521
status: Done
---

## Item

`encapsulate_field` of a field a statement both reads and writes: `a.road = b.road;`, `s.road = s.road.trim();` and, in the class itself, `s.road = road;` are refused ("reads and writes road. Split it: read the value into a variable, then assign it"). Write them as `a.setRoad(b.getRoad());`: the receiver, then the right side, then the write, the order the assignment had.

## Status

Done (2026-11-15): the setter call is written around the right side, so each read in it gets its own getter edit (`EncapsulateField.useEdit`; golden and probe `encapsulate_field_read_and_write`). The `freight-routes` reference is 9 steps, from 14.

## Evidence

The reference for `freight-routes` (roadmap #520): four `extract_variable` steps and a rewrite of `Shipment.copy` before the two `encapsulate_field` steps, which then rewrote 82 uses in 8 files. `extract_variable` of the whole right side `s.road.trim().toUpperCase(Locale.ROOT)` was refused too (a call that "could change road"), so only the read `s.road` could be extracted. A model that plans from names cannot see which lines need the split.
