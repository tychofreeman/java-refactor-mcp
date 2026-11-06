# Skeleton-only: refuse the bodies a command would write

- **Item:** the follow-up of `docs/sessions/ab-library.md` (the user, 2026-11-06: "Go ahead and
  widen the refusal"). The user also asked whether to refuse a body in any Java file; the answer
  given: no, a body's logic has to be typed somewhere, and refusing it would move it to
  `replace_statements` as skeleton-only first moved whole files into `add_declaration` (typing
  rose, 575 to 811 lines). What a command would have written is refused, and logic is not.
- **Date:** 2026-11-06
- **Branch:** skeleton-generated-bodies

## Plan

Under `Session.skeletonOnly`, three more refusals, each naming the operation that writes it:
1. `add_declaration` content with a constructor whose body only assigns each parameter to the
   field of its name (`this.owner = owner;`): `constructor`.
2. `add_declaration` content with an `@Override` method whose body only throws "not implemented"
   or only calls `super`'s method of its name with its parameters: `implement`.
3. `replace_statements` content that is one `try`, `if`, `for`, `while` or `synchronized`
   statement whose block holds the replaced statements' text (spacing aside): `surround`.
Off by default, as the flag is; goldens with `skeleton_only=true`.

Derivation: goldens first (three refusals, and an allowed constructor with logic); a class
`Generated` with the three checks (from `declaration`); calls in `Declarations.add` and
`Primitives.replaceStatements` when the flag is on.

## Notes

- read: the `skeleton-only` checks in `Edits.createFile` (the wording to follow).
- Goldens red (three refusals; the allowed constructor with logic already passed); one batch
  (`Generated` from `declaration`, its members, two call sites); all passed. The new file's
  ambiguous names (`Statement`, `Expression`, `Annotation`, passed up front) and the rest were
  settled by the package-files rule (#245) without a refusal.

## Measured

Through jrefactor: one batch and the docs; no refusals; by hand the goldens (resources) and this
record.

## Assessment

Through the tools, as planned. Whether the refusals steer models to the operations is for the
next skeleton run (the library set, ask first).

## Follow-ups

- An A/B of the skeleton arm on the library tasks, to see the refusals at work.
