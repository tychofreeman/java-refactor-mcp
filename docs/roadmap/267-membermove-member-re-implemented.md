---
order: 257
item: 267
status: Done
---

## Item

`MemberMove.member` re-implemented `Declarations.find` and had drifted: no erased names (#408), and for two overloads written alike its several-methods refusal suggested `Ex.raise(T)`, which matched both again, a loop for `move`, `pull_up`, `push_down` and `extract_supertype`; the dotted-path type lookup was copied in `MoveType.find`, `MoveToPackage` and `Stubs` (issue #483, code review)

## Status

Done 2026-11-13: `Declarations.memberName` (the name read, a `MemberName` record) and `Declarations.overloads` (written, then erased) are shared by `find` and `member`; every handed-back method name is `Declarations.methodLabel` (`MemberMove`, `PullUp`, `MoveMember.withAlong`, `Introduce`, `Fold`, `EnumField`), and `MemberMove.label` became `Declarations.writtenLabel`, a key for `Generated`. `Declarations.typePath` is package-visible and used by `MoveType.find`, `MoveToPackage` and `Stubs`. Goldens `move_overload_erased`, `move_refused_overloads_written_alike`

## Evidence

Issue #483; `docs/sessions/member-lookup.md`
