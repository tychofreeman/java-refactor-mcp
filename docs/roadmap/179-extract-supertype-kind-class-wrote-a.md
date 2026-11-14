---
order: 172
item: 179
status: Done
---

## Item

`extract_supertype` `kind=class` wrote a concrete class with no constructor, refused under an abstract old superclass, for an abstract member, or under a superclass without a no-argument constructor, with a fix line that did not fit (issue #409; the follow-up "a superclass constructor that takes arguments is not passed on" of `docs/sessions/extract-supertype-generic-class.md`)

## Status

Done 2026-11-13 (`ExtractSupertype.leavesAbstract`, by `Skeleton.leftAbstract`; `ExtractSupertype.constructors`). Defaults to reopen (the owner said take the smallest): the new class gets a constructor only for each old-superclass constructor the class's `super(...)` calls use, plus a no-argument one beside them when one calls `super()` implicitly, over copying every constructor of the old superclass; each keeps the access of the constructor it passes to, over `protected` or package-private; the class's own `super(...)` calls are not changed; the result says `extracted abstract class`, over saying nothing. `MemberMove.removal` now also takes the blank line before a last member (it had looked one line too far up, so `pull_up` and `extract_supertype` of a class's last member left a blank line before the closing brace)

## Evidence

The Commons Lang stress run, `docs/sessions/stress-new-options.md`; `docs/sessions/fix-supertype-constructors.md`
