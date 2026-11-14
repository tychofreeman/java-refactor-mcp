---
order: 254
item: 264
status: Done
---

## Item

Generated names could be Java keywords (issue #480): `convert_loop` named the element of `ints` `int` (`Names.elementName`), and `create_file` `declaration` or `implement` named a library method's parameter of type `Long` `long` (`Skeleton.parameters`); both were refused as the tool's fault, since the result did not parse

## Status

Done 2026-11-14: `Names.free(name, taken)` (a number after it while taken or a keyword), `Names.takenIn(node)` and `Names.fresh(name, node)` (was `LoopToStream.unused`, moved); `Names.elementName` gives `item` for a keyword; `Names.parameterName` (Stubs' guard: `value` for a keyword) for `Skeleton`. Goldens `convert_loop_index_keyword_name`, `create_file_declaration_keyword_parameter`. The other fresh-name loops (`Stubs`, `ThrowsCallers`, `TestFor`, `InlineFunction`, `Build`, `Surround`) are left to their owners' changes and #481

## Evidence

