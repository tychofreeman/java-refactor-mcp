# Convert a class to a record (roadmap #88)

- **Item:** roadmap #88. The user's list of 2026-11-04.
- **Date:** 2026-11-04
- **Branch:** convert-to-record

## Decisions (this session)

- **A new tool, `convert_to_record`:** no existing tool's option fits.
- **What qualifies:** final instance fields without initializers (the
  components, in order), one constructor that only assigns each field from its
  parameter in order (the canonical one, removed), no superclass, no subclass in
  the project, not abstract, no instance initializer.
- **Generated methods:** a record's `equals`, `hashCode` and `toString` compare
  and list its components; for a class that does not define them, that changes
  what equality and printing do. Under the 2026-11-04 policy (normal-flow
  behavior is kept), that is refused unless the model passes
  `value_semantics=true`; the result names what the record now generates.
- **Accessors:** a method `x()` of a component must be public in a record, so it
  is widened; getters (`getY()`) stay as they are.

## Plan

1. Red: goldens `convert_to_record` (with a caller, a non-public accessor, a
   getter, Javadoc), `_refused_equality`, `_refused_mutable` (red as an unknown
   tool).
2. Green: `create_file` of `ConvertToRecord`; `CONVERT_TO_RECORD` in `Tools`.

## Notes

- All three goldens passed on the first run after the change.

## Measured

All Java through jrefactor (`create_file`, `add_declaration`, `replace_text`),
no refusals; one large write (new).

## Assessment

- **Through the tools or around them:** through.

## Follow-ups

- A static nested class; a compact canonical constructor for one that
  validates its arguments.
