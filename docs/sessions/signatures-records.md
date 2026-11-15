# `Signatures`' records to `MethodFamily` (#528, last leftover)

- **Item:** issue #528 item 2's last part: the records `Source`, `Decl`, `Call`, `Lambda` and
  `Reference` move from `Signatures` to `MethodFamily`, with `move` of a member type with `to`
  (roadmap 513, slice 1).
- **Date:** 2026-11-15
- **Branch:** signatures-records

## Plan

Five calls of `move name=Signatures.X to=MethodFamily` in one batch: the four records that name
`Source` first (their `Source` is qualified as `Signatures.Source` on the way), then `Source`,
whose references inside `MethodFamily` (the four records' fields among them) become `Source`.

## Notes

- plan held: one batch of five `move` steps, each type-checked; 46, 48, 9, 3 and 46 references
  rewritten in ten files, two imports `Signatures` no longer used removed. No refusal.
- read: only the five records' lines (`inspect` `pattern`) before the batch, and the diff after.
- bulk: about 190 lines changed in ten files by one call.

## Measured

Not measured: the session-log hook is not loaded here. Java changes through the tools: 100% (one
batch; `spotless:apply` for line breaks).

## Assessment

Through the tools: what the friction log of 2026-11-15 (signatures-family) recorded as a 100-line
hand edit across seven files was one call.

## Follow-ups

None for #528 item 2.
