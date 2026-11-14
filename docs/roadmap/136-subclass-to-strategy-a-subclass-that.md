---
order: 129
item: 136
status: Done
---

## Item

`subclass_to_strategy`: a subclass that differs only by overriding one method becomes a function the base class holds; the override's body a static method, a field set in each constructor, `new Sub(...)` `new Base(..., Sub's function)`, the base's own body the default

## Status

Done 2026-11-12 (`SubclassToStrategy`; the user's choices: the model names the type or the tool declares a nested interface, over java.util.function when it fits; old constructors kept, delegating, over the parameter added to every call; the subclass deleted and its uses retyped, over keeping it as a holder). Open: bodies that use the instance (the function would take it as a parameter)

## Evidence

Issue #332 (2026-11-10, a downstream project): tools did the scaffolding (`extract_function`, `move`, `add_parameter`, `replace_expression`); four edits were by hand, each right only because of which function each instance held, one of them the override's delete (#322)
