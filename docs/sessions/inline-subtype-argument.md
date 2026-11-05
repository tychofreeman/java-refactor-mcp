# inline_function: a subtype argument passed on to a call

- **Item:** a gap met during roadmap #98 (shared helpers): folding a delegating
  copy (`Signatures.end(node)` as `return Nodes.end(node);`) into its callers with
  `inline_function` was refused, since a caller passes a `SimpleName` for the
  `ASTNode` parameter and #78's rule allows a subtype only at call receivers.
- **Date:** 2026-11-05
- **Branch:** inline-subtype-argument

## Decisions (this session)

- **A use as an argument is safe for a subtype when overload resolution cannot
  change:** the callee is neither generic nor varargs, and no other method of its
  name and number of parameters exists in its class or supertypes (an override
  counts as another, conservatively). Then the call with the subtype resolves to
  the same method, and the type of the parameter mattered nowhere else.
- **Its own PR,** ahead of #98, which needs it: the change of a tool is one
  item, the refactoring that uses it another.
- `receiverOnly` is renamed `subtypeSafe` (three declarations, `rename`).

## Plan

1. Goldens `inline_function_subtype_argument` (red: refused) and
   `inline_function_refused_subtype_overload` (an overload for `String`; refused
   before and after).
2. `soleOverloadArgument` (`add_declaration`), its use (`replace_text`).

## Notes

- `refusal:` in #98's batch, `inline_function` of `Signatures.end`; answered by
  this change, then the batch is resent.

## Measured

All Java through jrefactor, no refusals on this branch.

## Assessment

- **Through the tools or around them:** through; the refusal was answered by
  widening the tool, not by editing the callers by hand.

## Follow-ups

- A constructor call's argument (`new X(node)`) is not covered yet.
