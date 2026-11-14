# Member removal edits: one blank-line rule

- **Item:** issue #504 (code review of 2026-11-13, signatures #16 and moves #7), roadmap #359,
  group 3 of tracking issue #532
- **Date:** 2026-11-14
- **Branch:** one-member-removal

## Plan

Red: one golden per rule that differs from `MemberMove.removal` (a line of spaces between
members for `replace_declaration` and `move`, a last method for `inline`), and one for
`MemberMove.removal`'s own gap (a member right after: it took the blank line before and joined
two members, where its comment says "for the last member"). Green: `MemberMove.removal` checks
that a `}` follows before taking the line before; the three other sites call it
(`InlineFunction.removal` made a delegate, then `inline`).

## Notes

- plan: the four goldens were red for the blank-line reason only (diffed).
- refusal: `replace_statements` in `MoveMember` with an end line one short left
  `new TextEdit(start, end, "")` without its locals; the stubs then wrote a constructor into
  `TextEdit` and the batch was refused. `retry` with the right line.
- plan: no existing golden changed: in the usual layout (one empty line between members) all four
  rules gave the same text.

## Measured

`JREFACTOR_LOG` (`bin/session-report`): 5 `bin/jrefactor` calls, 1 refused, 0 by hand; one
batch of four steps (two `replace_declaration`s, two `replace_statements`), its `retry`, and one
`inline`.

## Assessment

Through the tools; the plan held.

## Follow-ups

None.
