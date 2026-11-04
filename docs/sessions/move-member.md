# move_member (the ninth of the ten higher-level tools)

**Item:** move a method of a top-level class to another top-level type in the same file.
**Source:** the composite comparison of 2026-11-04 (IntelliJ's Move Member, Fowler's Move Method),
option B.
**Date:** 2026-11-04

## Choice (the user, 2026-11-04)

Uses of the moved method's own class's instance state are handled by a receiver parameter: the
method becomes static in the target, takes the source instance as its first parameter (named
`receiver`), and each call passes the instance. Chosen over refusing instance-state uses (same file,
or any project type). The user's options for the target were not asked: v1 takes the target as a
top-level type of the same file.

## Plan

Change at the highest level: a method moves from its class to a named type in the same file, the
instance it used becomes an argument, and every call in the project is rewritten to pass it.

1. Find the source type and the method (`Type.method`, overloads by parameter list). Refuse a field,
   a constructor, a method with no body, an `@Override`, a method another project class overrides, a
   generic class, a method with a `receiver` name in use, and a body with an anonymous or local class,
   a `super` use, a call of itself, or a method reference to it.
2. The moved text: `static` (for an instance method, the receiver parameter first), `private`
   dropped (widened to package access, named in the result), this (unqualified) becomes `receiver`,
   unqualified instance fields and methods of the source get `receiver.`, unqualified statics get
   the source's name.
3. Each call across the project: `Type.m(recv, args)` for an instance (receiver = the expression, or
   `this` unqualified in the source type), `Type.m(args)` for a static. Refused: an unqualified call
   from another class or an anonymous class, a static called through an expression, a nested call.
4. Remove the method from the source (with its blank line), append the moved text to the target,
   rewrite the calls, apply through `MemberMove.gate`.

Arguments: `file`, `name` (`Type.method`, or `Type.method(params)`), `to` (the target type).

## Known difference

A null receiver: `a.m()` with a null `a` threw at the call. After the move, `Type.m(null)` runs and
fails only when the body uses the receiver. The moved method's behavior on a null receiver differs;
no static check sees it. The stress probes (`docs/sessions/stress-new-tools.md`) are the place for it.

## Golden cases (written first)

- `move_member_simple`: `charge` uses the field `rate`, is called from `total`; it moves to `Fees`
  with `receiver` first.
- `move_member_refused_override`: an `@Override` method is refused.
- `move_member_refused_anonymous`: a method that builds an anonymous class is refused.

## Notes

- `hand:` the three golden cases' `in/` and `out/` files, written before the tool existed.
- `hand:` `MoveMember.java` (about 400 lines) and its registration, written by hand. New behavior: the
  receiver rewrite of the body, the call rewrite, the private widening and the refusals.
- `plan:` the target is limited to a type of the same file (a default for v1, not asked): a move to
  another file needs the target's file, imports and its class index.

## Measured

Not measured (no session log for this branch). The goldens pass on the first run; the stress probes
for evaluation order and name capture are the next step (`docs/sessions/stress-new-tools.md`).

## Assessment

- **Through the tools or around them:** the tool could not write its own body; written by hand.
- **Did the plan hold?** Steps 1-4 were used. Two refusals were added beyond the plan: a call inside
  another call's argument (the rewrite would overlap), and a generic class.
- **Known difference:** a null instance. Not checked by any test here.
- **One change that would have helped:** a probe on a real project for evaluation order and name capture.

## Follow-ups

- Move to a type in another file.
- Refuse or allow an unqualified call from a nested class of the source.
- Widen what a private field used by the moved method needs (today the gate refuses).

## Follow-ups

- Move to a type in another file (the target's file gets the method and its imports).
- Refuse or allow an unqualified call from a nested class of the source.
- Widen what a private field used by the moved method needs (today the gate refuses).
