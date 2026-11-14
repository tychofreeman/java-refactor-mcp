---
order: 217
item: 225
status: Done
---

## Item

`convert_loop` and `loop_to_stream` compared a receiver with the walked collection by text: `this.items.remove(s)` in a loop over `items` was not seen as changing it (`to=for` turned a loop that threw `ConcurrentModificationException` into one that skips elements), and an index loop's `items.get(i)` was taken as the element read even when a body local `items` hid the walked field

## Status

Done 2026-11-13 for the loop tools: `LoopToStream.sameVariable` compares two expressions by variable binding key when each is a name or `this.f`, else by text; `ConvertLoop.unchanged` and `elementRead` and `LoopToStream.checkBody` use it. `TypeSwitch.chain` (2026-11-14) compares each test's selector by `LoopToStream.sameVariable` and refuses a chain of more than one test whose selector is no variable (`Names.variable`, moved from `LoopToStream`), since the call reads it once (the issue's suggestion, a local, parameter or final field, was widened to any field: no code runs between the tests); probe `type_switch_chain_selector_call`, golden `type_switch_to_method_refused_chain_call`

## Evidence

Issue #489 (code review); probes `convert_loop_to_index_this_field`, `convert_loop_to_each_shadowed`
