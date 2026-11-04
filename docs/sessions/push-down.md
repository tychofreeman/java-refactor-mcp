# push_down: move a method to its direct subclasses

- **Item:** roadmap order 25 (#17), the second half: `push_down`, methods. The
  user asked on 2026-11-04 to continue with the items not yet done after
  `pull_up` (`docs/sessions/pull-up.md`): push_down, then fields, then overloads,
  one PR each. This is the first of them.
- **Date:** 2026-11-04
- **Branch:** push-down

## Decisions taken without asking (the obvious defaults)

- Target: every direct subclass in the project, as Eclipse's push-down does. Each
  one gets the method; a subclass that does not use it still has it, which is
  harmless.
- Refused: no direct subclass in the project; a subclass that already declares the
  method; a method that overrides something the superclass declares (moving it
  down would change what the class runs, and the gate cannot see that).
- A call through the class's own type (`s.hello()` with `Sub s`) is refused by the
  gate: the method no longer exists on `Sub`.
- Methods only, one overload at a time (as `pull_up`).

## Plan

The change, at the highest level: a method moves from its class to each direct
subclass; the class no longer has it, and the gate refuses what then fails to
compile.

Operations, in order:
1. Red goldens: `push_down_method` (the method moves to the one subclass) and
   `push_down_refused_used_by_caller` (a caller of type `Sub` breaks, refused).
2. Implement `PushDown` beside `PullUp`: find the method, the direct subclasses
   (every project type whose superclass is `Sub`), the refusals, the edits, the gate.
3. Refactor: the removal, the insertion at a type's end, and the gated apply are
   the same in `PullUp` and `PushDown`; move them to one place (`MemberMove`) and
   keep both goldens green.

The one edit that changes behaviour: none outside the new tool.

## Notes

- `plan:` the record was written before reading the code.
