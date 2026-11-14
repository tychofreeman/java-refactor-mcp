# One "is this name qualified" predicate

- **Item:** issue #505 (code review, moves #10), roadmap #494.
- **Date:** 2026-11-14
- **Branch:** qualified-name-predicate

## Plan

Reproduce first: the review suspected `subclass_to_strategy` with `this::m` and `super::m`. Then
`Nodes.qualified(SimpleName)` by `getLocationInParent()` (MoveType's list, plus
`NameQualifiedType`), a unit test of every form, and each of the five sites through it.

## Notes

- read: `inspect` `names` of the five predicates, `pattern` for their callers.
- red: the review's cases did not reproduce as described (`this::m` is caught as a `ThisExpression`,
  `super::m` by its bare name, with the wording "uses move"); the inverse did:
  `subclass_to_strategy_method_reference` (`String::trim` refused as "a member of the instance").
  `inline_refused_super_method_reference`: `inline` caught `super.m()` but not `super::m`, and the
  gate refused the compile error it wrote.
- plan: `NameCheck`'s predicate counted any name whose parent is a super call or a method
  reference, so a direct argument of `super.m(x)` and the receiver of `x::m` skipped the meaning
  check; by place in the parent, they are checked now. No golden changed.
- refusal: a batch deleted `NameCheck.qualified` before replacing its use (safe delete refused);
  `retry` moved the two deletions after their uses, without resending the rest.
- tool: `inline` of the one-line `MoveType.unqualified` wrote `inside && (!Nodes.qualified(n))`;
  one `replace_text` dropped the parentheses (friction log).
- bulk: one batch of 11 steps (with one retry), three single calls.

## Measured

Not measured: the session-log hook is not loaded here (the CLI log, `JREFACTOR_LOG`, is).
From the notes: no Java by hand.

## Assessment

Through the tools. The review's example was the wrong way round; the shared predicate fixed the
case that did reproduce.

## Follow-ups

None.
