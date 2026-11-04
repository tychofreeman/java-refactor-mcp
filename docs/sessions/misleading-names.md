# Misleading names: Extraction's declared-inside map, Stubs' implicit

- **Item:** roadmap order 42 (#49): `Extract` maps each variable to "declared
  inside", so `used.get(r)` reads as "used"; `Stubs`' `implicit` is set as
  `!created.contains(...)`, the inverse of its name. Chosen with "Do them all
  in order" (the third of three).
- **Date:** 2026-11-03
- **Branch:** misleading-names

## Plan

`Extraction.Plan` holds the set of variables declared inside the statements
(`declaredInside`), not the map of all variables to a flag, so `callOf` asks
`declaredInside().contains(r)`. The map stays inside `plan()`, where the loop
needs it. `Stubs`: check the name against its use before renaming.

## Notes

- `read:` `Extraction.variables()` (what the map's value is), and each use of
  `used` in `Extraction`, to see what the boolean means.
- `read:` `Stubs` lines 283-292 and the `created` set (where it is added), to
  check `implicit`.
- `plan:` `implicit` is not renamed. Its name matches its use: when nothing
  declares a constructor, the default constructor is implicit and a stub keeps
  it, because declaring another constructor would take it away. The condition
  `!created.contains(...)` excludes classes this call created; the reason is not
  in the code, and this change does not guess it. Left as a question below.
- `hand:` none. Three `replace_text` steps in one batch, checked at the end.

## Measured

Not measured: no session log for this branch.

## Assessment

Through the tools: one batch of three `replace_text`, checked once; the
extraction goldens pass unchanged. The name fix is the `Plan` component, the
only change in behavior-free code. For `Stubs`, the plan's first reading was
wrong: the flag is not the inverse of its name, but the condition's reason is
not in the code, so the reading stopped there and nothing was changed.

## Follow-ups

- `Stubs`: why a class created by this call gets no default-constructor stub
  (`!created.contains(...)` in the constructor handler). Needs the author's
  reason, or a golden that shows the case; then rename or comment.
- `MoveType` still says "would introduce" (`docs/sessions/rename-on-gate.md`).
