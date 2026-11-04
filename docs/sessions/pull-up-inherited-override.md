# pull_up refuses a method that would override an inherited one (roadmap #69)

- **Item:** roadmap #69, found by a probe in
  `docs/sessions/stress-new-tools.md`: pulling `Sub.name()` into `Base
  extends Root`, where `Root` declares `name()`, applied and type-checked,
  and `Other extends Base` then returned "sub". The user asked this session
  to fix #67-#70 (2026-11-04). Checked first: no branch or PR of the other
  session touches it.
- **Date:** 2026-11-04
- **Branch:** pull-up-inherited-override

## Plan

The change: a method pulled into a supertype must not take the place of a
concrete method that supertype inherits, since the supertype itself and its
other subclasses would then run the pulled body. An abstract one is fine:
every concrete class below already declares its own.

1. Red: golden `pull_up_refused_inherited_override` (the probe's three
   classes and `Other`), by `create_file`.
2. Green: in `PullUp.run`, after the "already declares" check, a
   `replace_text` calling a new `overridden(IMethodBinding, ITypeBinding)`
   (by `add_declaration`): the first concrete method of the supertype's
   superclasses and interfaces that the moved method overrides, or null.
   Refused with what it overrides and the fix: keep it in the subclass, or
   pull it up to the class that declares the inherited one.

## Notes

- `read:` `PullUp.run` (lines 26-101) and `MemberMove`'s `appendTo` and
  `textOf`, to place the check and to plan #70.
- The golden was not run red before the fix: the change went in the batch
  right after `create_file`. The probe in `docs/sessions/stress-new-tools.md`,
  on the same classes, applied before the change, which is the red it
  would have shown. A slip in TDD order, not a tool's gap.

## Measured

From `bin/session-report` (before the docs edits):

- tool calls: 11
- through jrefactor: 10 edit steps (6 `create_file`, 3 `replace_text`,
  1 `add_declaration`)
- refused by jrefactor: 0
- by hand: 2 (the record; a shell command), then the docs by `sed`, a
  heredoc and Python
- large writes: 2 (the refusal and `overridden`)
- reads: Bash java 1, plus one `inspect` of `PullUp` made before the branch

## Assessment

- **Through the tools or around them:** through for the Java and the
  description; the docs by shell.
- **Large writes:** `overridden` (25 lines) and the refusal (27), new.
- **Did the plan hold?** Yes, except the red run (see Notes).
- **Reading:** to place the check.
- **One change that would have helped:** none.

## Follow-ups

- The refusal does not look at whether other subclasses exist: a supertype
  with no other subclass, abstract, still refuses. Narrow it if that turns
  out to get in the way.
