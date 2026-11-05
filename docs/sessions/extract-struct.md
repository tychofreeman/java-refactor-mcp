# Extract class and introduce parameter object (roadmap #89)

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

## Second part: extract class (branch extract-class)

- **Decisions (this session):** `extract_struct` with `fields` (and `field`, the
  holder's name) in place of `parameters`. The named instance fields move into a
  new package-private class in its own file; the class holds one with `private
  final Address home = new Address();` in the first field's place, and every use
  goes through it (`home.street`, `this.home.street`). Refused in this first
  version: a field used from another class (encapsulate it first), an
  initializer that reads anything but constants (it would run at the holder's
  initialization, not its own place), a final field without an initializer (a
  constructor could not assign it through the holder), a static field, a holder
  name the class already uses.
- **Plan:** red goldens `extract_struct_fields`, `_fields_refused_outside`,
  `_fields_refused_initializer`; then `ExtractStruct.fields` by
  `add_declaration`, the dispatch by `replace_text`, the schema.
- **Notes:**
  - The goldens were red (an unknown argument), then green but for
    `ServerTest.descriptionsHaveNoRunsOfSpaces`: my first `replace_text` of the
    schema was refused (its `old` did not match after formatting), a later one
    put the description's new lines two spaces off, and the text block's lines
    then carried runs of spaces. `hand:` a Python dedent of that block, since
    re-indenting every line of it by `replace_text` was longer.
  - Measured (`bin/session-report`, this branch): Java through jrefactor but the
    one Python dedent; one refusal (above), answered with a second batch.

## Follow-ups

- Extract class: fields used from other classes (with the holder widened), and
  moving the methods that use only the moved fields (`move_member` to the new
  class).
