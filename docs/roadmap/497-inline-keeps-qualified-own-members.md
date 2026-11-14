---
order: 487
item: 497
status: Done
---

## Item

`inline` of a static method whose body names a static member of its own class qualified by the
class (`return Bindings.declarationKey(b);` inside `Bindings`) was refused for a call in another
class, as if the name were unqualified ("the body of erasedKey uses a member of Bindings, and the
call in ... is in another class"). `InlineFunction.usesMembersOf` counted every name that binds
to a static member of the class.

## Status

Done 2026-11-14: `usesMembersOf` skips a qualified name (`Nodes.qualified`), so
`Keys.erased(n)` with body `Keys.declared(name) + Keys.PREFIX` inlines to
`Keys.declared(n) + Keys.PREFIX` in another package. An unqualified use is still refused there
(`inline_function_refused_other_class`). A caller that reaches the method through a static
import is refused by the type check, since the import names the deleted method (pinned; a
follow-up).

## Evidence

Issue #621; friction log 2026-11-14 (one-parameter-rules: `inline` of `Bindings.erasedKey` done
as `move` then `inline`). Goldens `inline_function_qualified_own_member`,
`inline_function_qualified_own_member_static_import`; probe `inline_function_qualified_own_member`
(a caller declaring its own `declared` and `PREFIX` prints the same).
