---
order: 514
item: 524
status: Done
---

## Item

A probe that expects a refusal names a fragment of it (`refused=` in `probe.properties`), as a
golden case's `refused.txt` does

## Status

Done (2026-11-16): `ProbeTest` fails a refused probe without `refused`, or whose refusal does not
contain it. All 101 refused probes got a fragment that names the rule (65 distinct phrases, none
with a line number). Two had been refused for their arguments, not their rule, and are fixed:
`add_parameter_hides_field` sent `value=2`, which the harness reads as a number ("value must be a
string; got 2."), now `value.txt`; `fold_overload` named `Main.show`, which has two overloads
("Name one with its parameter types"), now `Main.show(Object)`. Both tools refuse for the right
reason once asked properly, so no tool changed.

## Evidence

A review of the tests (the owner, 2026-11-16): `ProbeTest` took any `Refusal` as the expected
outcome, so a probe passed on a refusal of anything, a typo in `args.properties` included; 101 of
the 199 probes expect a refusal.
