# super not refused when code moves to another object

- **Item:** issue #472 (code review of 2026-11-13, control-flow #3), roadmap #240
- **Date:** 2026-11-13
- **Branch:** fix-lambda-super (stacked on fix-type-switch-names, #468)

## Plan

`super` means the superclass of the object the code runs in, which changes when a body moves
between an anonymous class and a lambda, or into a case's type. Refuse an unqualified
`SuperMethodInvocation`, `SuperFieldAccess` or `SuperMethodReference` in `ConvertLambda.refuseSelf`
(beside `this`), and any of them in `TypeSwitch.usesOnly`, which `EnumSwitch` shares. A probe per
path first: anonymous to lambda, lambda to anonymous, a type switch, an enum switch.

## Notes

- plan: `super.getClass().getName()` gives a deterministic probe for the lambda paths (`p.Main$1`
  against `p.Main`); `super.describe()` with two superclasses for the type switch. All four probes
  changed output before the fix, `EnumSwitch` too (the issue marked it suspected).
- One `batch` of two `replace_text` steps (check `end`), imports added by the tool; two more
  `replace_text` calls to reword the refusals.
- refusal: the super-reference golden first hit "several start on line 7" (a lambda and a method
  reference); `column=12` in its args.
- Seen in passing: anonymous to lambda reports "to an anonymous class" for a lambda with a block
  body (`ConvertLambda.run`'s result checks `replacement.contains("\n")`).
- Docs by hand: not Java.

## Measured

Not measured: the session-log hook is not loaded in this session. Three tool calls to `src/main`,
no hand edit of Java.

## Assessment

Through the tools; the plan held.

## Follow-ups

- `convert_lambda`'s result wording above.
