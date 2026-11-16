---
order: 511
item: 521
status: Open
---

## Item

`encapsulate_field` of a field a statement both reads and writes: `a.road = b.road;`, `s.road = s.road.trim();` and, in the class itself, `s.road = road;` are refused ("reads and writes road. Split it: read the value into a variable, then assign it"). Write them as `a.setRoad(b.getRoad());`: the receiver, then the right side, then the write, the order the assignment had.

## Status

Open

## Evidence

The reference for `freight-routes` (roadmap #520): four `extract_variable` steps and a rewrite of `Shipment.copy` before the two `encapsulate_field` steps, which then rewrote 82 uses in 8 files. `extract_variable` of the whole right side `s.road.trim().toUpperCase(Locale.ROOT)` was refused too (a call that "could change road"), so only the read `s.road` could be extracted. A model that plans from names cannot see which lines need the split.
