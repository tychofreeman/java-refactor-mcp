# Safe delete inside a batch: only the uses that survive it count

- **Item:** issue #623, the owner's follow-up of 2026-11-14 (friction log 2026-11-13,
  fix-parameter-argument-exceptions); the owner chose a batch-aware check
- **Date:** 2026-11-14
- **Branch:** safe-delete-batch

## Plan

Before each step, the batch tells the session which declarations its later steps delete;
`SafeDelete.check` drops the uses inside those declarations. Goldens first: two-method cycle in
both orders, three-method cycle across files, a cycle with an outside caller (refused, naming it),
and the default check `"each"`.

## Notes

- read: `SafeDelete.check`, `Declarations.replace` (its caller), `Batch.run` and `Batch.steps`
  through `inspect`.
- plan: under `"each"` the gate type-checks every step, so a delete whose remaining callers go
  later can never pass there; the rule applies under both, and the batch's existing hint ("send the
  batch with check \"end\"") is the refusal. Pinned by `batch_delete_cycle_refused_each`.
- plan: "survives" is read from the later steps' deletes only, not from running them: a default,
  recorded in `docs/design.md`.
- hand: none in Java. One batch of eight steps; three of its `replace_text` replacements had
  `new` from an `@@` section, which drops its last newline while `old` (inline JSON) kept its own,
  so two lines were joined three times; two more `replace_text` calls split them.

## Measured

Not measured: the session-log hook is not loaded here. Three `bin/jrefactor` calls for Java (the
batch and two fixes), none by hand.

## Assessment

Through the tools. The plan held; the joined lines were my mix of an inline `old` ending in a
newline with a section `new`.

## Follow-ups

- A use that a later `replace_text` or a replacing `replace_declaration` removes still counts.
