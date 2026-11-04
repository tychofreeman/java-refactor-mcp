# encapsulate_field (the tenth of the ten higher-level tools)

**Item:** a field becomes private, with a getter and a setter, and every use in the project goes
through them.
**Source:** the composite comparison of 2026-11-04 (IntelliJ's Encapsulate Fields), option B.
**Date:** 2026-11-04

## Choice (the user, 2026-11-04)

Bean-style accessors: `getX` and `setX` from the field's name, with optional `getter` and `setter`
arguments to override them. Chosen over requiring the names every time.

## Plan

1. Find the field of a top-level type (`Type.field`). Refuse a field of several declarators, and a
   final field that is written (its writes stay in the constructor, which this does not change).
2. Make the field private: its access modifier is replaced by `private`.
3. Rewrite the project's uses: a read becomes the getter call (`getX()`, or `obj.getX()` for a
   qualified use); a plain `x = value;` statement becomes `setX(value);`. A write in any other form
   is refused: a compound operator (`+=`), `++` or `--`, a write inside an expression, or a statement
   that also reads the field.
4. Append the getter and the setter to the type (the setter is not made for a final field).
5. Apply through `MemberMove.gate`.

Arguments: `file`, `name` (`Type.field`), `getter` and `setter` (optional).

## Golden cases (written first)

- `encapsulate_field_simple`: a read in the class, a write in a method, a read from another class.
- `encapsulate_field_refused_compound`: `rate += 1` is refused.
- `encapsulate_field_refused_read_and_write`: `rate = rate + 1` is refused.

## Notes

- `hand:` the golden cases' `in/` and `out/` files, written before the tool existed.
- `hand:` `EncapsulateField.java` (about 260 lines) and its registration, written by hand. New behavior:
  the use rewrite (read, statement write, refusals) and the accessors.
- `plan:` the optional getter and setter arguments were not in the plan; the user's choice needed them.

## Measured

Not measured (no session log for this branch). The goldens pass on the first run.

## Assessment

- **Through the tools or around them:** the tool could not write its own body; written by hand.
- **Did the plan hold?** Steps 1-5 were used. Refusals are the choice for writes the tool does not
  rewrite (compound, ++, expressions); a rewrite of those is a follow-up.
- **One change that would have helped:** a probe on a real project, for reads and writes through
  inheritance and through a field of another class.

## Follow-ups

- Rewrite compound writes and `++` (`setX(getX() + 1)`).
- A getter for a boolean field (`isX`).

## Follow-ups

- Rewrite compound writes and `++` (`setX(getX() + 1)`).
- A getter for a boolean field (`isX`), and a setter that refuses a value by a check.
