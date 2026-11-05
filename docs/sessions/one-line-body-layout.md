# Members added to a one-line body are laid out like the rest (follow-up of #70)

- **Item:** the two layout problems found while checking #70's case in the
  other member-adding tools (2026-11-04): `add_declaration` into `class C {}`
  indents the member 4 spaces where the project's tools write 2;
  `encapsulate_field` on `public class F { int x; }` leaves the field on the
  header line with a trailing space and puts the accessors below it. Both
  compile. The user chose to fix them in one small PR over logging them.
- **Date:** 2026-11-04
- **Branch:** one-line-body-layout

## Plan

The change: a member added to a type whose body is on one line ends up as
if the body had been written over several lines.

1. Red: goldens `add_declaration_one_line_body` (2 spaces) and
   `encapsulate_field_one_line_body` (the field on its own line, the
   accessors after a blank line), by `create_file`.
2. Green:
   - `Declarations.memberIndent`: the last fallback step 2, not 4, so that
     it matches what `MemberMove.appendTo` and `EncapsulateField` write.
     (A project with 4-space indentation still gets 4 whenever the file or
     the content shows a step; only a file with no indented line falls back.)
   - `MemberMove.appendTo` returns the edits as a list: when the body is one
     line with members (`{ int x; }`), the space after `{` becomes a line
     break and indent, and the space before `}` the blank line and the
     member. Two edits, so other edits inside the existing members (the
     `private` that `encapsulate_field` adds) do not overlap. Its four
     callers take `addAll`.

## Notes

- `read:` `Declarations.memberIndent` and `step` (lines 568-605), and
  `EncapsulateField` lines 130-176, to find where each layout came from.
- Both goldens red first (layout differs), green after one batch of seven
  steps: `replace_declaration` of `appendTo`, `replace_text` at its four
  callers (`add` to `addAll`) and two in `Declarations`.
- `plan:` the fallback step is a choice: 2 matches what this project's other
  tools write; 4 is common Java. Only a file with no indented line and
  one-line content reaches it, so I took 2 without asking.

## Measured

From `bin/session-report` (before the docs edits): 7 `create_file` and 7
edit steps through jrefactor in two batches, no refusals; the record and
docs by hand.

## Assessment

- **Through the tools or around them:** through.
- **Large writes:** `appendTo` again (27 lines), most of it new.
- **Did the plan hold?** Yes.
- **One change that would have helped:** the four `add` to `addAll` edits
  were a return-type change made at each caller by hand-written
  `replace_text`; a `change_type` for a method's return type (it does
  fields only) would not have edited the callers either. Not logged: four
  sites.

## Follow-ups

- `add_declaration` into a one-line body with members (`{ int x; }`) was not
  probed.
