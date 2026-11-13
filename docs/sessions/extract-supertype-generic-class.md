# extract_supertype from a generic class: a superclass

- **Item:** roadmap order 138 (#145): increment 2 of `docs/sessions/generics-sketch.md`,
  `extract_supertype` with `kind=class` from a generic class.
- **Date:** 2026-11-13
- **Branch:** extract-supertype-generic-class

## Plan

Written after #144 (`ExtractSupertype.generics` exists), before reading `PullUp`'s helpers.

The change: the new class declares the type parameters its members and the old superclass's
type use; the class extends it with their names.

1. Goldens, red: `extract_supertype_generic_class` (no old superclass),
   `_generic_class_superclass` (the sketch's example 9). Probe
   `extract_supertype_generic_class`, expect applied. The golden that pinned #144's interim
   refusal goes with the refusal.
2. The behavior change in `run`: drop the interim precondition; `generics` over the members'
   declarations and the superclass's type; the header and the two places that write the
   class's `extends` take `generics.declared()` and `generics.named()`.
3. `PullUp` finds the supertype by erasure and moves text whole: read, expected unchanged.
4. Docs: the contract, the roadmap row, session notes, the tool's description if it needs a
   sentence.

## Notes

- bulk: two golden cases and the probe written as plain files; the golden
  `extract_supertype_refused_generic_class` removed with the refusal it pinned.
- plan: the red run was skipped: that removed golden had pinned, in #144, that `kind=class`
  from a generic class is refused, so the new cases could only fail on it.
- read: `PullUp.supertype` and `MemberMove.member` by `names`; the class branch of `run` by
  lines. `PullUp` matches the supertype by its erasure's name: unchanged, as the sketch guessed.
- One batch of five `replace_text`, check `"end"`. The members are now looked up in `run`
  before anything is written (they feed `generics`), so a member not found is refused naming
  `extract_supertype`, not `pull_up`.
- Goldens and the probe passed on the first run (555 golden cases, 97 probes).
- The tool's description gained one sentence (what the new type declares from a generic
  class); docs through one batch of ten `replace_text`.
- hand: none in `src/main/java`.

## Measured

Not measured: the session-log hook is not loaded in this container. From the notes: 2 batches,
15 steps, 4 `inspect` calls; no hand edit of Java.

## Assessment

Through the tools. The plan held; the change was 18 lines in `run`, since #144 had made it
easy. Nothing in `PullUp` changed.

## Follow-ups

- A superclass whose constructor takes arguments is still not passed on (the first version's
  limit, generic or not).
- `Opened.topLevel` refuses a record as "not a top-level type": roadmap #155.
