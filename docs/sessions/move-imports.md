# move takes imports; the project's own class is preferred

- **Item:** roadmap #131, found by the A/B check of the merged list (2026-11-06,
  `docs/sessions/ab-merged-tools.md`): `move` was refused for an ambiguous `Order` and told to pass
  `imports`, which it did not take.
- **Date:** 2026-11-06
- **Branch:** move-imports

## Plan

1. An ambiguous simple name, when neither the file's prior imports nor its packages decide, takes
   the only candidate in the project's own sources (`shop.order.Order` over JUnit's), named in the
   result; a refusal suggests the project's class first, then `java.*`.
2. `move` and `move_member` take `imports`, passed to the import fix of every file they write;
   `move`'s other routes refuse it, naming the one that takes it.

Derivation: goldens first; `Imports.chosen` gets the third rule; `MemberMove.gate` an overload
with choices; `MoveMember.run` and `field` take them; the `Def`s and the router.

## Notes

- read: `Imports.chosen` and `toImport`; the `MOVE_MEMBER` and `MOVE_TYPE` `Def`s; the gate calls
  in `MoveMember`.
- One batch of 14 steps (two `old`s shifted by #106) and one `insert_statements`; no refusals;
  both goldens and the rest passed first time.
- `move_type`'s own import fix still takes no choices: not hit yet; `move` refuses `imports`
  there rather than drop it.

## Measured

Through jrefactor: one batch, one edit, the docs by script; by hand the goldens (resources) and
this record.

## Assessment

Through the tools, as planned.

## Follow-ups

- `imports` for `move_type` and `move_file` routes, if a run hits an ambiguity there.
