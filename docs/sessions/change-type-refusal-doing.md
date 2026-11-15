# change_type: the gate's refusal says "changing"

- **Item:** issue #643, roadmap item 510
- **Date:** 2026-11-15
- **Branch:** change-type-refusal-doing

## Plan

`SignatureChange.apply` uses one string for the result and the refusal. Make the refusal's string
a parameter of its own: extract the body of the five-argument `apply` into a method, add `doing`
to it with `what` as the value at its one call (so nothing changes), rename it to `apply`; then
the gate gets `doing`. Red: pin "changing the ..." in the two refusal goldens of `change_type`.
Green: `Signatures.checked` passes its `doing`.

## Notes

- read: `inspect` of `Signatures.checked`, the change_type call, `SignatureChange.apply`,
  `Gate.refusal`, `ChangeType` lines 75-100 (already "changing").
- refusal: `replace_expression` takes `expression`, not `old`; `batch` `retry` fixed it.
- plan: the first batch also made the behavior change before the goldens were red; undone with
  `undo`, the refactoring sent alone, the goldens seen red, then the one `replace_expression`.
- Java edits: extract_function, add_parameter, rename, replace_expression (2), move_parameter,
  replace_text (the modifier and the doc comment over the new overload); none by hand.

## Measured

Not measured (no session-log hook in this session).

## Assessment

Through the tools. The composition extract, add_parameter with the old argument as its value,
rename back to the overloaded name gave a new parameter whose callers stay unchanged; it may be
worth a line in `docs/design.md` beside the other derivations if it recurs.

## Follow-ups

The other callers of the five-argument `apply` (add/remove/move parameter, change_signature,
extract_type, function_to_method) still word a refusal in the past tense; each can pass its own
`doing` through the new overload.
