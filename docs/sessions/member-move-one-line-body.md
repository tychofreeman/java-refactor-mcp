# pull_up and push_down into a one-line `{}` body (roadmap #70)

- **Item:** roadmap #70, found by a probe in
  `docs/sessions/stress-new-tools.md`: `pull_up` into `class Base extends
  Root {}` wrote a syntax error, refused as "Move only a member that uses
  nothing the subclass has". The user asked this session to fix #67-#70
  (2026-11-04).
- **Date:** 2026-11-04
- **Branch:** member-move-one-line-body

## Plan

The change: a member added to a type goes inside its braces, wherever the
closing brace is. `MemberMove.appendTo` inserts at the start of the closing
brace's line, which for `{}` on one line is the start of the class header.

1. Red: goldens `pull_up_one_line_body` and `push_down_one_line_body`, by
   `create_file`.
2. Green: `replace_text` in `appendTo`: when the closing brace's line has
   other text before the brace, insert just before the brace, with a line
   break first. `push_down` shares it.

## Notes

- Both goldens red first with the syntax error (`push_down` had the bug
  too, unprobed before), green after the one `replace_text`.
- No `read:` beyond `appendTo`, read while planning #69.

## Measured

From `bin/session-report` (before the docs edits):

- tool calls: 7
- through jrefactor: 12 edit steps (11 `create_file`, 1 `replace_text`)
- refused by jrefactor: 0
- by hand: 1 (the record), then the docs by `sed` and a heredoc
- large writes: 0

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes, as written.
- **One change that would have helped:** none; the refusal's wrong advice
  ("Move only a member that uses nothing the subclass has") for a syntax
  error the tool itself wrote is worth noting: the gate's refusal text is
  the tool's guess at the cause. A syntax error in a tool's own output
  could get its own wording ("the tool wrote code that does not parse:
  a bug, report it"). Not built.

## Follow-ups

- Other tools that add a member at a type's end (`add_declaration`,
  `move_member`, `encapsulate_field`) were not checked for `{}` on one line.
