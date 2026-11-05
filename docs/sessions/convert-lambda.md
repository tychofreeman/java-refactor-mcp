# Lambda, anonymous class and method reference (roadmap #87)

- **Item:** roadmap #87: each to the others where the meaning is kept. The
  user's list of 2026-11-04; `inline_function` and `move_member` refuse method
  references and ask for a lambda first.
- **Date:** 2026-11-04
- **Branch:** convert-lambda

## Decisions (this session)

- **One tool, `convert_lambda`, with `to`,** over three or six tools: the node
  is found the same way (file, line, column), and only the target form differs.
- **Meaning kept by refusal:** a method reference evaluates its receiver once,
  a lambda at each call, so a receiver that may change (a field that is not
  static final, a call) is refused either way; `this` and bare calls of the
  interface's or `Object`'s methods mean another object in an anonymous class
  than in a lambda, so they are refused across that boundary.
- **Names for a reference's lambda parameters:** the first letter of each
  parameter type's name, made unique and unused in the member (`s` for a
  `String`).
- Lambda to method reference and anonymous class to lambda are one step each;
  method reference to anonymous class is two (to a lambda first), as the
  refusal says.

## Plan

1. Red: goldens `convert_lambda_to_reference`, `convert_reference_to_lambda`,
   `convert_anonymous_to_lambda`, `convert_lambda_to_anonymous`,
   `convert_lambda_to_reference_refused` (red as an unknown tool).
2. Green: `create_file` of `ConvertLambda`; `CONVERT_LAMBDA` (`add_declaration`)
   and its place in `ALL` (`replace_text`).

## Notes

- All five goldens passed on the first run after the change.
- `bulk:` `ConvertLambda`, about 500 lines by `create_file`: new behavior.
- Probe on Commons Lang through `GateCostTest`: `thread::join` in
  `ThreadUtils` (line 528, a `FailableBiConsumer<Long, Integer>`) became
  `(l, i) -> thread.join(l, i)` and type-checked (259 ms), then was undone.

## Measured

From `bin/session-report` (before the docs edits): all Java through jrefactor,
no refusals, one large write (new).

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes.

## Follow-ups

- A method reference with type arguments (`Foo::<T>m`); `Outer.this::m`.
- An anonymous class whose method throws checked exceptions the interface
  method allows, and generic interfaces with wildcards.
