# Stub type variables by name, when in scope

- **Item:** the open decision in roadmap #36 (`Stubs.typeName` and
  `Extract.typeName` disagreed on type variables). The user chose the
  recommended rule, 2026-11-04.
- **Date:** 2026-11-04
- **Branch:** stubs-type-variables-in-scope

## Plan

The rule, in one line: a stub writes a type variable by its name when the
type the stub goes into declares that variable (or an enclosing type it can
see does), and erases it otherwise.

Operations, in order:

1. `typeVariablesOf(owner)`: the names of the target type's type parameters,
   and those of its enclosing types up to a static one.
2. `method` and `constructor` set the round's `inScope` from the target,
   before the target is erased.
3. `typeName` returns a type variable's name when `inScope` has it; the
   existing erasure stays for everything else.

## Notes

- `plan:` the first try was "by name everywhere", as the user's earlier
  answer read. A probe (a generic caller `<T> void keeps(T item)` calling
  `shelf.put(item)` on a class without `put`) showed it refuses: the stub
  `put(T item)` cannot compile in `Shelf`, so the gate refused the call. The
  erasure already in the code writes `put(Object item)`, which compiles. The
  by-name change was reverted and the choice was put to the user with three
  options. The user chose the recommended one (in scope by name, erased
  otherwise).
- `read:` `Stubs.method`, `Stubs.constructor` and `typeName`, to find where
  the target type is known and where `typeName` is called.
- `bulk:` none; the change is a helper of 12 lines, a field, and two
  assignments. Written by Edit.
- `hand:` the code and the goldens were written by hand (Edit), not through
  the tools: this is the java-refactor-mcp source itself, and the tools cover
  edits to it only when the change is a rename or a signature change.
  No refusal from the tools led to this.
- Goldens: `stubs_type_variable_in_scope` (red before the change: the stub was
  `store(Object item)`; green after: `store(T item)`) and
  `stubs_type_variable_erased` (the out-of-scope case, green before and after).

## Measured

`./mvnw verify`: 169 tests, 2 skipped, spotless clean. The session-log hook
was not measured for this session.

## Assessment

- **Through the tools or around them:** the change was made by hand. Writing
  it through `replace_declaration` would have needed the helper's whole
  method body, not a few lines; the edits were small enough that Edit was the
  shorter route. That is a hand edit to note, not a tool gap.
- **Did the plan hold?** Yes, with the rule changed by the user between the
  plan and the code: the first plan (by name everywhere) was reverted before
  any golden was written.
- **Reading:** `Stubs.method`, `constructor` and `typeName`, to find the target.

## Follow-ups

- Wildcards: `Stubs` erases `? extends X`, `Extract` keeps it. Decide when a
  stub needs a wildcard parameter; not done.
- Captures: both erase them; nothing to decide yet.
