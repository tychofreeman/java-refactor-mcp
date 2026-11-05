# More changes to exceptional behavior reported (roadmap #75, part)

- **Item:** roadmap #75, the follow-up of #73: report more changes to where an
  exception is thrown. The user said "continue".
- **Date:** 2026-11-04
- **Branch:** exceptional-more

## Plan

- `inline_function`: an argument that may throw (`Effects.mayThrow`) whose
  parameter the body uses never, or only in a branch, a lambda or an anonymous
  class (`deferral`), threw at the call and now throws sometimes or never.
- `inline_variable`: an initializer that may throw, moved into a use inside an
  `if` (not its condition), a loop, a `?:` or `&&`/`||` branch, a lambda, a
  `switch` or a `try`, threw at the declaration and now throws only there, or is
  caught.
- Both report through `Exceptional.change`, so `preserve_exceptions` refuses them.

Red goldens with `exceptional.txt`: `inline_variable_may_throw_guarded`,
`inline_function_may_throw_unused`; then the two checks by `replace_text`.

## Notes

- Both red first, then green; no other golden reported anything (GoldenTest
  requires an empty report without `exceptional.txt`).

## Measured

All Java through jrefactor (`create_file` of the goldens, `replace_text` of the
checks), no refusals.

## Assessment

- **Through the tools or around them:** through.

## Follow-ups

- A dereference moved past a guard by `move_statement` (it refuses moving past a
  branch only by the normal-flow rules); `extract_variable` out of a `try`.
