# inline_function accepts a subtype argument where it means the same (roadmap #78)

- **Item:** roadmap #78, the other session's follow-up of `effect-order.md`: a
  subtype argument (`String` for `CharSequence`) was refused by the conversion rule.
- **Date:** 2026-11-04
- **Branch:** inline-function-subtype

## Decisions (this session)

- A subtype argument is accepted when every use of its parameter in the body is
  the receiver of a call (`s.length()`): a call dispatches on the runtime type, so
  the argument's static type changes nothing there. Every other use stays refused:
  passed to a call (another overload could be chosen), a field reached through it
  (a subclass may hide the field), the body's value itself (the call site's
  overloads would see the subtype).
- Subtyping is by qualified name along the supertypes: the argument and the
  parameter come from different parses.

## Plan

Red goldens `inline_function_subtype_receiver` and `_subtype_refused_passed`; then
`receiverOnly` per parameter (`replace_text` in `run`, `add_parameter` twice to pass
it), and `subtype` and `useCount` (`add_declaration`).

## Notes

- Both goldens passed on the first run after the change; the rest unchanged.
- A no-op `Arrays.fill` left in the first batch was removed by `replace_text`.

## Measured

All Java through jrefactor (two `add_parameter`, `add_declaration`, `replace_text`),
no refusals.

## Assessment

- **Through the tools or around them:** through.
