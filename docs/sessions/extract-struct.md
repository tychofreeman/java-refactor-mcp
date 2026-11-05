# Introduce parameter object (roadmap #89, first part)

- **Item:** roadmap #89 (gorefactor's `extract_struct`): extract class and
  introduce parameter object. This PR is the parameter object; extract class
  is the item's open part.
- **Date:** 2026-11-04
- **Branch:** extract-struct

## Decisions (this session)

- **gorefactor's name, `extract_struct`, with `parameters`:** the group
  becomes a record (`public record Size(int width, int height) {}`), Java's
  struct, in a new file beside the method's class. `fields` is left for extract
  class.
- **Consecutive parameters only:** grouped into `new Size(a, b)` at the first's
  place, arguments that were apart would be evaluated in another order.
- **The family changes together** (as the signature tools do): every override
  and implementation, each body's uses (`width` becomes `size.width()`), every
  call. A body that assigns a grouped parameter is refused (a record's
  component cannot be assigned), and so is a family that lambdas implement.

## Plan

1. Red: goldens `extract_struct_parameters`, `extract_struct_refused_apart`.
2. Green: `SignatureChange.replaceRange` (`add_declaration`), `ExtractStruct`
   (`create_file`), `EXTRACT_STRUCT` in `Tools`.

## Notes

- Both goldens passed on the first run after the change (red as an unknown tool).

## Measured

All Java through jrefactor (`create_file`, `add_declaration`, `replace_text`), no
refusals; one large write (`ExtractStruct`, new).

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** For the parameter object, yes; extract class was not
  started.

## Follow-ups

- Extract class: fields of a class and the methods that use them moved into a
  new class held by a field.
