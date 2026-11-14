---
order: 349
item: 359
status: Done
---

## Item

A member was removed with four blank-line rules: `MemberMove.removal` (the blank line after, else the one before, even with a member right after, so two members were joined), `replace_declaration`'s delete (the blank line before, else an empty line after, not a line of spaces), `move` of a method (an empty line after, else the one before only before a `}`) and `inline` of a method (the blank line after only, so a last method left a blank line before the `}`) (issue #504, code review)

## Status

Done 2026-11-14: all four use `MemberMove.removal`, which now takes the line before only for the last member, as its comment said, and counts a line of spaces as blank. Goldens `replace_declaration_delete_blank_spaces`, `inline_function_last_method`, `move_member_blank_spaces`, `pull_up_field_keeps_blank_before`; no existing golden changed. Default to reopen: the last-member rule, over taking the line before whenever there is none after

## Evidence

Issue #504; `docs/sessions/one-member-removal.md`
