# push_down's override check (issue #465)

- **Item:** code-review issue #465: `push_down` skipped its override check for a name with
  parentheses and looked only at the direct superclass's declared methods.
- **Date:** 2026-11-13
- **Branch:** fix-push-down-override

## Plan

Reproduce first: a probe where `C.f` (no `@Override`) overrides a grandparent's `f` and a
subclass of `C` exists; goldens for `Sub.hello()` against a superclass's `hello` and for an
interface's default method. Then the one edit: ask the method's binding what it overrides over
every supertype (`CallTargets.overridden`, already shared) in place of the name comparison over
the superclass, and drop the parameter that no longer serves.

## Notes

- read: `inspect` with `names` (PushDown, Qualified) and a line range of `PushDown`; `pattern` for
  the existing supertype walks (`overridden|overrides\(|getInterfaces\(\)`).
- plan: probe and two goldens failed as the issue said (probe: output 2 then 1).
- `replace_declaration` of `PushDown.refuseOverride`, `replace_text` for the loop that threw on its
  first item, `remove_parameter` of the unused `subType`. Nothing by hand in Java.
- The resource files (probe, goldens) by shell heredocs, as the brief says.

## Measured

Not measured: the session-log hook is not loaded here.

## Assessment

Through the tools; the plan held. `CallTargets.overridden` was already the walk the issue asked
for (transitive, interfaces, abstract included), so no new helper was needed. `Object`'s methods
now count too: pushing down an un-annotated `toString` changes what the class prints.

## Follow-ups

- `PullUp.overridden` and `SafeDelete.overridden` skip abstract methods on purpose (they ask
  "what would run instead"); the shared helper the review's finding 6 names could take a flag.
