# change_type of a field: parse the type, refuse [] after the name

- **Item:** issue #492 (code review of 2026-11-13, signatures #17), group 2 of tracking issue #532
- **Date:** 2026-11-13
- **Branch:** fix-field-change-type

## Plan

Reproduce with two goldens: `int counts[]` to `long[]`, and `type=long)`. Then the duplicates the
issue names, by extract and fold: the new parameter's name and type checks into
`Signatures.checkNewParameter`, the return type's preconditions into
`Signatures.requireReturnType`, the "names a method" routing of `TypeParameters` into one helper.
Then the two checks in `ChangeType.run`: `parsesAs`, and a refusal for `extraDimensions`.

## Notes

- plan: both goldens failed as the issue said: the dimensions case applied (`long[] counts[]`),
  the syntax case was refused about a use of `total`, not the type.
- read: `inspect` of `ChangeType.run`; `sed` of the duplicate sites (line ranges, after `grep`
  found them, as their numbers had moved since the review).
- refactor: `extract_function` twice in `Signatures`, then one batch of `replace_text` (doc comments,
  visibility) and `fold` twice; `fold` reached `ChangeSignature` but not `EnumField` (friction log).
- edit: `replace_text` for `EnumField`; a batch of `add_declaration` and two `replace_text` for
  `TypeParameters.namesMethod`; a batch of two `replace_text` for `ChangeType`.
- The refactorings ran while the two new goldens were red: they touch only `Signatures`,
  `EnumField` and `TypeParameters`, which those cases do not reach.
- hand: the goldens and the docs.

## Measured

Not measured: the session-log hook is not loaded here. All Java through the tools.

## Assessment

Through the tools. The plan held; one fold missed a qualified call (logged).

## Follow-ups

- `fold` matching a static call written with its own class's qualifier (friction log).
- The identifier rule in `checkNewParameter` (`SourceVersion.isName` accepts `a.b`) is issue #494.
