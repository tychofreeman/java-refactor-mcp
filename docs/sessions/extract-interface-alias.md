# extract_interface: an alias of extract_supertype

- **Item:** the user's request (2026-11-06), after gorefactor renamed its `extract_interface` to
  the shared `extract_supertype` (its #601): a model used to the old name reaches the Java tool.
- **Plan:** a golden calling `extract_interface` (red), then the entry in `Tools.ALIASES`.

## Notes

## Measured

Through jrefactor: one batch of 2 `replace_text` steps; by hand the golden (resources), the docs,
this record.

## Assessment

Through the tools, in TDD order.

## Follow-ups

None.
