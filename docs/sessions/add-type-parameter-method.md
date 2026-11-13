# add_parameter with type_parameter on a method

- **Item:** roadmap #158 (order 151): increment 6 of `docs/sessions/generics-sketch.md`
  (example 3). The owner decided the sketch's open questions by its recommendations (2026-11-13).
- **Date:** 2026-11-13
- **Branch:** add-type-parameter-method

## Plan

Refactor first: the loop of `TypeParameters.typed` that writes `T` for each type that is `value`
becomes `retype` (extract_function), for the method side to share. Then `toMethod`, reached when
`name` is a method: `MethodFamily.of` (references kept) gives the declarations, calls, lambdas
and references; refuse a lambda, `members`, a constructor and `value` in the return type only;
declare `<T>` and `retype` each declaration's signature; one gate; then compare each call's and
reference's types with what they were, and `CallTargets`. Goldens from the sketch's increment 6
list, one probe.

## Notes

- bulk: `toMethod` (about 250 lines) as one `add_declaration`, its helpers as a second, the
  routing and wording as `replace_text`, in one batch.
- plan: `MethodFamily` with references kept keeps only references that implement a family member,
  not those that name the method (`Lists::first`): `toMethod` finds those itself by key. The result
  names calls and method references apart.
- refusal: none from the tools; the reworded static-member refusal changed one golden from #157.
- hand: goldens, the probe and docs (resource and Markdown files).
- read: `inspect` for every Java read.

## Measured

Not measured: the session-log hook is not loaded in this container.

## Assessment

Through the tools for every Java read and write; `extract_function` did the refactoring step.
The plan held.

## Follow-ups

- A call that would infer another type is refused; writing `Lists.<String>first(...)` there is
  issue #375 (increment 8), not built.
- Methods of records and enums: `value` is resolved by a probe field in a class or interface
  body; a record's or an enum's body needs its own place for it.
