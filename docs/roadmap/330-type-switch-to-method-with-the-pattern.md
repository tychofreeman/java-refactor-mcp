---
order: 320
item: 330
status: Done
---

## Item

`type_switch_to_method` with the pattern variable used inside an anonymous or local class of a case body (issue #551): `c.toString()` became `toString()` and a bare `c` became `this`, both then meaning the inner class's object, which type-checked and ran differently

## Status

Done 2026-11-14: `TypeSwitch.asReceiver` refuses such a use (`inClass`), naming the fix: a local declared before the class (`Circle circle = c;`), used inside it, which the tool then writes as `Circle circle = this;`. The enum forms go through the same `asReceiver`. Over writing `Circle.this` (the issue's other option): the smaller change, and the refusal's fix gives the same code through the tool. Probe `type_switch_anonymous_class`; goldens `type_switch_to_method_refused_anonymous`, `type_switch_to_method_anonymous_local`

## Evidence

