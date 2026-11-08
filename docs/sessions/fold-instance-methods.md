# fold of instance methods, in the class and its subclasses

- **Item:** roadmap #130, its second part (gorefactor's `docs/parity.md` section 4: `fold` of
  instance methods, #103). The user chose where a match may be (2026-11-08): in the class and its
  subclasses, over the same class only and over any receiver.
- **Date:** 2026-11-08
- **Branch:** fold-instance-methods

## Plan

The change: `fold` takes an instance method too. Code matches its body where `this` is the same
object: an instance member of the method's class or of a subclass, directly in that class's body
(not an anonymous, local or nested class), and becomes the unqualified call `m(args)`. The body's
field reads match the same fields by binding, as names do now.

Derivation:
1. Red: goldens `fold_instance_method` (a match in the class, one in a subclass, one left in an
   anonymous class), `fold_instance_refused_overridden` (a subclass overrides the method).
2. Green, in `Fold.run`: let a non-static method with a body through; refuse it when a project
   method overrides it (a second `Holding` on the method's name, one parse, `overrides`); the call
   unqualified.
3. Green, in `Fold.leave`: for an instance method, `this(c.root())`, the first type around the
   code with no static member between, must be the class or a subclass; in a subclass, not a
   private method nor a body that uses `super`; `overloaded` asked from that type.
4. Docs: the description, contract, roadmap, parity.

## Notes

- read: `Fold.run`, `collect`, `matchRuns`, `leave` and `overloaded` (62-515), `Holding.of`: where
  the static rule is, how names match (by binding key, so an inherited field already matches in a
  subclass), and where a match is left.
- bulk: goldens and a probe (resources, by shell), new tests.
- bulk: `overrider`, `thisOf` and `typeAround` (69 lines) through `add_declaration`, and the
  precondition with the override check (16 lines), new behavior.
- plan: held; `overloaded` took the type to look from (`from`), so a subclass's own overload is
  seen too.

## Measured

From `bin/session-report`: 13 edit steps through jrefactor (one batch of six for the code, one of
three for the descriptions, the docs one call each), no refusals; by hand only the record and test
resources; reads: 6 `inspect`.

## Assessment

- **Through the tools or around them:** through them; the code passed every test, the result text
  of the golden included, on the first run.
- **Did the plan hold?** Yes.
- **Reading:** `Fold` is 800 lines; reading its run and leave was needed to place the rules.
- **One change that would have helped:** none.

## Follow-ups

- #130's last part: line/column addressing on the signature tools (the user's choice: `line` of
  the declaration or of any call, `column` for two on a line).
- A match through another receiver (`p.count * p.price` to `p.total()`) is the option not chosen.
- gorefactor's `docs/parity.md`: fold of instance methods is no longer behind (its own PR, with the
  encapsulate_field one).
