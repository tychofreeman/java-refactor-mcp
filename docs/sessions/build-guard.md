# `build` with `guard` and `guard_null`

- **Item:** roadmap #164 (order 157), the generators sketch's increment 6: a guard entry, a
  condition (or a null test) and its exit.
- **Date:** 2026-11-13
- **Branch:** build-guard

## Plan

A guard infers little (the sketch says so): its value is the checks. `Build` learns two keys,
`guard` and `guard_null`, read by `exits` (exit, value, message, each refused where the exit
does not take it). The entry is written as typed (`Entry.guarded`, the exit one step in), so
`scope` skips it; `types` checks it in the last draft (`checkGuard`): the condition's type, a
primitive against null, the return value against the enclosing method or lambda, a loop or
switch for continue and break (`Effects.loopOf`), and a checked exception against the method's
`throws` and the tries around it (`Surround.checked` and `caught`, made package-private).

## Notes

- `read:` `Build.check`, `Signatures.label`, `Effects.loopOf`, `Surround` through `inspect`.
- `hand:` the twelve golden cases (resource files), with the shell, as the brief allows.
- `refusal:` the first batch was refused: `Extract.typeName(r, s)` with a statement made the
  stubs declare an overload that was ambiguous with `typeName(ITypeBinding, ITypeBinding)`.
  The fix was `around(s)`, as `types` does; resent whole (one step changed).
- `plan:` the goldens found `article` gave "a int": it tested capital vowels only.
- Through the tools: one batch of fourteen steps (`replace_text`, `add_declaration`), one of
  two for the schema and description, and one `replace_text`.

## Measured

Not measured: the session-log hook is not loaded in this session.

## Assessment

Through the tools; the largest step was `checkGuard` (about 90 lines) as one
`add_declaration`. The plan held. The refused batch cost one resend; `retry` with step 10
alone would have sent less.

## Follow-ups

- A guard inside a lambda says "the lambda" in its refusals; no golden covers a lambda yet.
