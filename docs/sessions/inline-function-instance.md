# inline_function inlines instance methods (roadmap #84, third part)

- **Item:** roadmap #84, widening existing tools: `inline_function` for
  instance methods (it refused them: "an instance method needs its receiver").
- **Date:** 2026-11-04
- **Branch:** inline-function-instance

## Plan

- The call's receiver replaces `this` in the body, and goes before each
  instance member the body names bare (inherited ones too). A call with no
  receiver or `this` keeps the body as written.
- Refused: a method that a declaration in the project overrides (the call
  dispatched; the inlined body would not); a receiver that is not fixed (the
  body would evaluate it at each use); a body that uses `super`.
- A receiver that may be null failed at the call; inlined, it fails where the
  body uses it, or not at all: a change to exceptional behavior, reported (and
  refused under `preserve_exceptions`), per the 2026-11-04 policy.
- `usesMembersOf` (the other session's rule for a body naming its class's
  members at a call in another class) now counts static members only: an
  instance member gets the receiver, so it means the same.

1. Red: goldens `inline_function_instance` (with `exceptional.txt`),
   `_instance_refused_overridden`, `_instance_refused_receiver`.
2. Green: the static precondition removed; `receiverUses`, `receiverEdits`,
   `refuseOverridden` and `qualified` by `add_declaration`; `substituted`
   takes the receiver's edits (`replace_declaration`); the call site and the
   report by `replace_text`.

## Notes

- `refusal:` the first batch: `Modifier` ambiguous after the precondition
  that used it was removed in step 1 (the import went with it); sent with
  `imports`. Then two "old is not in" refusals: indentation of 8, not 10, for
  `checkNesting`, and 6, not 4, for `replacementFor`'s parameters. Each was
  sent again after a `grep` of the line.
- `refusal:` `replace_declaration` of `refuseOverridden` with a new parameter
  was refused for its caller, and the `replace_text` of the caller for the
  declaration; done as `add_parameter` (which changes both) and then
  `replace_declaration`, in one batch.
- The override check first compared bindings of two parses (the tool's own
  parse of the file, and `parseAll`'s), which never match; it takes the
  method's binding from `parseAll`'s parse now.
- `inline_function_refused_instance` (instance methods refused) is obsolete;
  deleted with `move_file` through `bin/jrefactor`, since this session's MCP
  server predates `move_file`.

## Measured

From `bin/session-report` (before the docs edits):

- tool calls: 21
- through jrefactor: 1 refactoring step (`add_parameter`), 32 edit steps
- refused by jrefactor: 5 (above), each answered by another call
- by hand: 0 Java; the obsolete golden deleted by `move_file` from the
  command line; the docs by heredoc and Python
- large writes: 3 (the receiver helpers, `substituted`, `refuseOverridden`)

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes, with the binding fix the golden found.
- **One change that would have helped:** within a batch, an import a step
  removes and a later step needs (twice now, `Annotation` and `Modifier`)
  could be kept, or chosen as the file had it. Logged in the friction log.

## Follow-ups

- A method that a class outside the project overrides is not seen.
- A receiver that is a field (`this.cart.total()`) is refused as not fixed.
