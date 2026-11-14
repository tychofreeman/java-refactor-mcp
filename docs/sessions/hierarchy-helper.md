# Hierarchy: one supertype walk

- **Item:** issue #495 (code review 2026-11-13, group 3 of #532): about twenty hand-written
  worklists over `getSuperclass()` and `getInterfaces()`, with different dedup, start type and
  identity (key or qualified name).
- **Date:** 2026-11-14
- **Branch:** hierarchy-helper

## Plan

One class, `Hierarchy`, with `supertypes(t, self)` (breadth-first, each once by erasure key),
`overridden`, `isSubtype` (by key) and `isA` (by name, for library types). Derivation:
`create_file` an empty `Hierarchy`; `move` `Extraction.ancestors` (already "t and every type
above it") into it, `rename` it `supertypes`, `add_parameter` `self` with `true`; then the
behavior change (dedup, `self`) test first in `HierarchyTest`. Each helper that is the whole walk:
`replace_declaration` to a delegate, then `inline`. Each walk mixed with per-type work:
`replace_text` of the loop. Pin the changes the goldens show.

## Notes

- read: issue bodies, `Overrides` (#538), each site by `inspect` with line ranges.
- refusal: `rename` with `name=Hierarchy.ancestors` and no `line`; batch `retry` with `line`.
- plan: the test's stubs (`overridden`, `isSubtype`, `isA`) were declared by `create_file`;
  `add_declaration` replaced them.
- refusal: a batch that moved `PullUp.overridden` into `Hierarchy` clashed with
  `Hierarchy.overridden`; retry with a `rename` to `concreteOverridden` before the `move`.
- refusal: two `replace_text` batches left a loop's tail (`todo.add`) behind; the end check named
  it and a retry added the step.
- hand: none in Java but the rebase conflict in `Stubs.removedInSupertypes` (#556 changed the
  same lines).
- bulk: the golden case `create_file_declaration_nearest_first` is plain resource files.
- Through the tools: 2 `move`, 2 `rename`, 1 `add_parameter`, 8 `inline` (refactorings); 2
  `create_file`, 1 `add_declaration`, about 15 `replace_declaration` and about 20 `replace_text`
  (edits), in 9 batches.

## Measured

Not measured: the session-log hook is not loaded here. Counts are from the notes.

## Assessment

Through the tools, all of it; the refactorings carried the moves and the removal of eight helpers
(with every call), and the edits carried each loop. Behavior changed in one visible place: the
stubs of `create_file` `declaration` and `implement` come nearest supertype first (`Skeleton`
walked depth-first), pinned by a golden case that failed on the old code. Other differences are
pinned by `HierarchyTest`: a diamond interface is visited once (`CallTargets.overridden` listed
its method twice), and subtypes compare by erasure key, not qualified name, so two local classes
(both named "") are not each other's subtypes (`Fold.fits`, `InlineFunction`'s argument check,
`Overrides`); an interface now counts as an `Object` in `ParameterValue`'s checks too. One change
that would have helped: a step that makes a method a delegate of another and inlines it (logged).

## Follow-ups

- The downward lookups ("does anything in the project override this": `InlineFunction`,
  `Fold.overrider`, `AddResult`, `MoveMember`, `Extraction`), the issue's `firstOverrider`;
  `Fold` is being changed under #486.
- `LoopToStream.elementOf` and the loop helpers' home (control-flow #17).
