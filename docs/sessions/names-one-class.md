# One class for type labels

- **Item:** roadmap order 36 (#43): `Stubs.label` duplicated `Rename.typeLabel`,
  and `Signatures.label` dropped the outer class that `typeLabel` keeps. Chosen
  with "Do all three in order" (the third of three).
- **Date:** 2026-11-03
- **Branch:** names-one-class

## Plan

One class, `Names`, holds `typeLabel` (`Outer.Inner`). `Rename.typeLabel` and
`Stubs.label` go; their callers call `Names.typeLabel`. `Signatures.label`
names its method's class with `Names.typeLabel`, which changes a nested
class's refusals from `Inner.send(String)` to `Outer.Inner.send(String)`. A
golden pins that first (red). Not in this item: `Stubs.typeName` and
`Extract.typeName` disagree on type variables and captures; merging them is a
choice of what a stub writes, so it waits for the user.

## Notes

- `plan:` the first plan deleted each helper in one `replace_declaration` and
  repointed its callers after. The delete was refused (callers remained), and
  the repoint's `replace_text` was refused (the definition would not parse).
  The two changes went in one `batch` with check `"end"`.
- `refusal:` `replace_declaration` deleting `Rename.typeLabel` refused: "would
  add compile errors ... undefined for the type Rename (x8)". Answered by the
  batch, not by hand.
- `refusal:` `replace_text` renaming `typeLabel(` in `Rename.java` refused: the
  definition itself would stop parsing. Answered by the batch.
- `plan:` deleting `Stubs.label` while its callers in `Stubs.Round` still used
  it did not refuse: the stub mechanism declared `Stubs.Round.label(ITypeBinding)`
  in the file, as the callers' missing method. Removed with
  `replace_declaration`, after the repoint. Friction-log entry written.
- `hand:` none. The golden (`sig_add_refused_nested_class`) was made with
  `create_file`, and its args edited with `replace_text`, after two trial
  runs: `name=send` (needs a value file, then a qualified name) and
  `name=Outbox.send` (the file has no type `Outbox`) found the naming rules.

## Measured

Not measured: no session log for this branch.

## Assessment

Through the tools: every source change went through `bin/jrefactor`. The one
new file (`Names.java`) was made with `create_file`. Refusals came from the
tools and were answered by a batch with the final check, which is what the
batch is for. The cost was the stub: a method deleted while still called was
replaced by a stub, not refused, so the file held a declared `label` until it
was removed by hand-like steps through `replace_declaration`.
The plan held for the labels; it did not hold for `typeName`, which was
left as a follow-up because merging it changes what stubs write.

## Follow-ups

- `Stubs.typeName` and `Extract.typeName` (roadmap order 36, open): decide
  whether type variables are kept by name or erased, then merge.
- `MoveType` still says "would introduce" (`docs/sessions/rename-on-gate.md`).
