---
order: 63
item: 70
status: Done
---

## Item

`pull_up` into a class whose body is `{}` on one line writes a syntax error, and the refusal blames the member ("Move only a member that uses nothing the subclass has")

## Status

Done (2026-11-04), local session; `push_down` had it too; record in `docs/sessions/member-move-one-line-body.md`

## Evidence

Found by a probe after `docs/sessions/stress-new-tools.md`. `MemberMove`; `push_down` into such a subclass was not tried
