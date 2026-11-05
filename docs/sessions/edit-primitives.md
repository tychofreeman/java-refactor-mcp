# Edit primitives (roadmap #91)

- **Item:** roadmap #91, the last of the user's 2026-11-04 list: gorefactor's
  `replace_expression`, `insert_statements`, `replace_statements`, `add_case`,
  `add_element` (option A of the ten-tools decision, now built beside them).
- **Date:** 2026-11-04
- **Branch:** edit-primitives

## Decisions (this session)

- **gorefactor's names, one class (`Primitives`), one PR:** the five are small
  edits that share finding by line, re-indenting, and `Edits.gated`.
- **Found as the other tools find things:** `replace_expression` as
  `extract_variable` (line, exact text, column; `ExtractVariable.find` made
  package-private); statements and switches by `Opened.startingOn`.
- **`replace_statements` with empty content deletes**, as `replace_declaration`
  does; a range that cuts a statement is refused with the lines that end one.

## Plan

1. Red: goldens `replace_expression`, `insert_statements`, `replace_statements`,
   `replace_statements_refused_cut`, `add_case`, `add_element` (red as unknown
   tools).
2. Green: `create_file` of `Primitives`; the five `Def`s by `add_declaration`.

## Notes

- `create_file` of `Primitives` declared a stub for `Args.tool()`, which it
  called and nothing declared; `add_declaration` of the real one replaced the
  stub. The stubs working as designed.
- Two goldens failed first: I took `Lines.end` for the offset of the newline;
  it is the offset past it. Three `replace_text` edits fixed the uses.
- `add_element`'s `content=3` would be read by `GoldenTest` as a number; the
  content went in `content.txt`.

## Measured

All Java through jrefactor (`create_file`, `add_declaration`, `replace_text`),
no refusals; large writes: `Primitives` and the five `Def`s (new).

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes, with the `Lines.end` fix.

## Follow-ups

- `add_element` for argument lists and annotations' arrays; `insert_statements`
  at the start or end of a block given the block's line.
