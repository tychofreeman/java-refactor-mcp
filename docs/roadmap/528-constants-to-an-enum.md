---
order: 518
item: 528
status: Done
---

## Item

Constants to an enum: `extract_type` with `members` the `static final int` or `String` constants of a class or interface makes them an enum in its own file, and the fields, parameters, locals, method results and record components that hold them follow; uses that would see another value are refused, or read back through `accessor` (issue #373; the enum sketch's slice 1, increments 9 to 11)

## Status

Done 2026-11-17 (`ConstantsToEnum`, entered from `ExtractType.run`; `accessor` and `value_semantics`; literals equal to a constant become it. Goldens `extract_type_constants_enum*` (14), probes `constants_enum_*` (5). Not run on Commons Lang yet, which the sketch suggests after increment 9)

## Evidence

`docs/sessions/enums-sketch.md`, slice 1; the user asked for the open issues on 2026-11-17
