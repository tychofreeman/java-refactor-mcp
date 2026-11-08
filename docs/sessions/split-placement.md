# Placement by kind leaves Declarations

- **Item:** roadmap #32 (split large classes when each is next changed). `Declarations` (750 lines)
  was changed by #279 and #280, which gave it placement by kind (`rank`, `place`, `Place`) and a
  second user, `MemberMove.addTo`. The user chose #32 among the four offered items (2026-11-07).
- **Date:** 2026-11-08
- **Branch:** split-placement

## Plan

The change: placement by kind is one type of its own, `Placement`, the record of where a member
goes with the two functions that compute it, used by `Declarations.plan` and `MemberMove.addTo`.

Derivation, refactorings only:
1. `move` of `Declarations.Place` to its own file (`Place.java`; uses become `Place`).
2. `rename` of `Place` to `Placement` (the file follows).
3. `move` of `Declarations.rank` and `Declarations.place` to `Placement`, every call following.
4. Doc comments: the record's says what it is now; the session notes' "where code goes" names it.

No test changes: the goldens are the check.

## Notes

- refusal: a batch of `move` of `Place` and `rename` at a guessed line 1 of the new file: the
  declaration was on line 6; the move went alone, then `inspect`.
- refusal: `rename` of `Place` to `Placement` (and to `Spot`) refused as captured at every use: a
  bug, records only (`isDeclaration` false for a record's name). Set the branch aside (stash), fixed
  it in its own PR (#286, `docs/sessions/rename-type-argument.md`), and came back.
- plan: `move` of a static method takes a class as its target, not a record ("Placement is not a
  class ... that resolves"), so the record stays `Place`, in its own file, and the two functions
  went to a new `final class Placement` (`create_file` with `declaration`).
- refusal: `move` of `Declarations.rank`: "used as a method reference"; prepared with
  `convert_lambda` to a lambda, then back to `Placement::rank` after the moves.
- refusal: the conversion back in the same batch named line 159, which the move had rewritten; sent
  as its own call.
- The session's MCP server predates #286, so the rename and moves went through `bin/jrefactor
  --rebuild` from the working tree.
- hand: none in Java; `Placement`'s class doc and private constructor through `replace_text`.

## Measured

From `bin/session-report`: 2 refactoring and 4 edit steps on the MCP server, 6 refusals (above),
and the CLI calls (one batch of four: `create_file`, `convert_lambda`, two `move`; then
`convert_lambda`), which the session log records as Bash; reads: 5 `inspect`.

## Assessment

- **Through the tools or around them:** through them; every line of the split was moved, none
  typed. Each refusal was answered with another operation or a preparing step, and one found a
  bug, fixed first.
- **Did the plan hold?** Not as written: `Place` was not renamed into `Placement`, since `move`
  does not take a record as target; two small types instead of one.
- **One change that would have helped:** `move` of a static member into a record (or an enum).
  Logged as a follow-up; `MemberMove.typeNamed` returns only a `TypeDeclaration`.

## Follow-ups

- `move` (and `pull_up`'s lookup, `MemberMove.typeNamed`) could take a record or an enum as the
  target of a static member.
- #32 for `Tools` (1,554 lines): its handlers and its descriptions could part when it is next
  changed in a way that needs it.
