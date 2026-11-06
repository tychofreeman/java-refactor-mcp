# inline class: inline's field route

- **Item:** roadmap #128 (gorefactor's `inline_struct`, parity section 3). The user's choices
  (2026-11-06): inline class as `inline`'s `field` route; `split` deferred (`extract_function` of
  a method's tail already splits it, and no friction entry asks for more).
- **Date:** 2026-11-06
- **Branch:** inline-class

## Plan

`inline` with `name` a class (`Person`) and `field` (`home`), whose type is a project class
(`Address`) that only that field uses: `Address`'s members take the field's place in `Person`,
every `home.x` becomes `x` (or `this.x` where a local of that name hides it), `home.m()` becomes
`m()`, and `Address`'s file is deleted: the inverse of `extract_struct` with `fields`. Refused:
the class used anywhere else, a superclass or interfaces, a constructor with a body, a field
initializer other than `new Address()`, `home` used other than to reach a member (passed,
compared, assigned), a member name `Person` already has.

Derivation: golden first; `InlineClass` from `declaration`; `inline`'s router takes `field`.

## Notes

- read: `TypeSwitch.fileOf` (widened, reused), `Gate.applyOrRefuse` (a null text deletes).
- One batch; the golden passed first time, with undo restoring the deleted file.

## Measured

Through jrefactor: one batch and the docs; no refusals; by hand the golden (resources) and this
record.

## Assessment

Through the tools. Passing the four ambiguous JDT names up front avoided the new-file import
refusal this time.

## Follow-ups

- `split` (deferred by the user's choice); a class with a constructor's work folded too.
