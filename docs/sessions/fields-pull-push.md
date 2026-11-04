# pull_up and push_down for fields

- **Item:** roadmap order 25 (#17), the field half of `pull_up` and `push_down`
  (`docs/sessions/pull-up.md`, `docs/sessions/push-down.md` left it open). Chosen
  by the user's standing order to continue with the open items on 2026-11-04.
- **Date:** 2026-11-04
- **Branch:** fields-pull-push

## Decisions taken without asking (the obvious defaults)

- The member is named `Sub.name`: a method if the class has one of that name, else
  a field. A name that is both a field and a method is refused, with both named.
- A field declaration with several variables (`int a, b;`) is refused: move one
  variable per declaration.
- The field keeps its modifiers and initializer. A `private` field moved up is
  refused by the gate when `Sub` still uses it.
- `pull_up`: refused when the supertype already declares a field of that name.
- `push_down`: refused when a subclass already declares a field of that name, and
  when no subclass in the project exists (as for methods).
- Calls and uses inherit through the class, so `pull_up` needs no call changes; a
  `push_down` caller through `Sub` is refused by the gate.

## Plan

Operations, in order:
1. Red goldens: `pull_up_field` (moves to the superclass, its use in `Sub` still
   compiles), `pull_up_field_refused_private` (a private field used by `Sub` is
   refused), `push_down_field` (moves to the one subclass), and
   `push_down_field_refused_used_by_caller` (a caller of type `Sub` is refused).
2. Generalize the member lookup in `PullUp` and `PushDown` to a method or a field,
   and `MemberMove.removal` and `textOf` to any body declaration.
3. Refactor only what the change needs; keep every golden green.

The one edit that changes behaviour: none outside the two tools.

## Notes

- `plan:` the record was written before reading the code.

## Measured

Not measured: no session log for this branch.
Verify: `./mvnw verify` passed, 207 tests, 0 failures, 4 skipped.

## Assessment

- **Through the tools or around them:** around, as in `pull-up.md` and
  `push-down.md`: the tools were not loaded in this session.
- **Did the plan hold?** Yes. The four field goldens went red ("declares no method"
  and "No tool"), then the general lookup in `MemberMove` made them green with
  `PullUp` and `PushDown` rewritten on it.
- **Refactor:** `MemberMove.member` and `declares` replaced the method-only lookup
  in both tools; the removal, insertion and gate code were already shared.

## Follow-ups

- Overloads for both tools: refused.
- A subclass nested in another type is still not found (top-level subclasses only).
