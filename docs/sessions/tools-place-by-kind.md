# The tools that add a member place it by kind

- **Item:** the follow-up of `docs/sessions/place-by-kind.md`: `pull_up` and `push_down` add a
  member at the end of the target, and `move` of a static field and `extract_variable` with `as`
  were not checked. The user's rule (2026-11-07): layout is the tool's, not the model's, so every
  tool that adds a member places it as `add_declaration` does.
- **Date:** 2026-11-07
- **Branch:** tools-place-by-kind

## Plan

The change: one placement for every member a tool adds to a type, `Declarations.place` by
`Declarations.rank`, so a field a refactoring moves or introduces lands after the target's
fields, as `add_declaration` puts it; methods still go at the end, which is what the rank gives
them.

Derivation:
1. Green: widen `Declarations.rank`, `Declarations.place` and `Place` to package-private, so
   `MemberMove` and `Introduce` can ask them.
2. Red: goldens for a field `pull_up`, `push_down` and `move` (a static field) into a class that
   has fields and methods, and an `extract_variable` `as=field` into a class whose first member is
   not a field; each expects the field after the last field.
3. Green: `MemberMove.addTo(text, type, moved, member)`: by `rank(member)`, `appendTo` for the
   end, else an insertion after `place`'s member or before the first; `PullUp`, `PushDown` and
   `MoveMember` call it with the moved member. `Introduce` inserts at `place(owner, 0)`.
4. Then `pull_up`'s and `push_down`'s descriptions, the contract, the session notes.

## Notes

- read: `MemberMove.appendTo`, `Introduce` lines 88-135 and `Declarations.place`, to see where each
  inserts and what `place` returns; `ExtractType` 392-410, to check that extract class's holder
  field already takes the first field's place (it does, so it was left).
- bulk: the four goldens (122 lines, by shell under `resources/`), new tests; two `Sub.java` and
  `Shop.java` copied from the existing goldens.
- bulk: `MemberMove.addTo` (24 lines), new behavior, through `add_declaration`.
- plan: `addTo` takes the rank, not the member, so `Introduce` (which has no member yet) uses it
  too, instead of its own insertion; that removed `Introduce`'s copy of the indentation lookup.
- hand: a grep of `GoldenTest.java` for a filter property, refused by the read guard; not needed.

## Measured

From `bin/session-report`: 33 tool calls; 16 edit steps through jrefactor (two batches of 8 and
6, and one of 2), no refusals; by hand only the record, the goldens (resources) and one
paragraph of `how-this-project-was-built.md`; reads: 12 `inspect`.

## Assessment

- **Through the tools or around them:** through them. Every Java change went in one batch of
  eight steps (three visibility edits, `addTo`, three call sites by `replace_expression`,
  `Introduce` by `replace_statements`), checked at the end, and passed the tests on the first
  run. The docs went through `replace_text` in one batch.
- **Large writes:** `addTo` is new behavior; the goldens are new tests.
- **Did the plan hold?** Yes, with the one change noted: `addTo` by rank.
- **Reading:** the bodies read were the ones being changed or reused; finding the call sites was
  one `inspect` pattern, though a broad first pattern returned many unrelated lines.
- **One change that would have helped:** none needed here. The broad pattern was mine.

## Follow-ups

- `move` of a method, `encapsulate_field` and `type_switch_to_method` add methods, which go at the
  end under this rule too, so they keep `appendTo`.
