# Tool descriptions: the refusals left, and extract_struct

- **Item:** the user, 2026-11-06, after reading `extract_struct`'s description: (1) remove the
  refusal wording #256 left (sentences not starting "Refuses, saying why"), (2) make
  `extract_struct`'s arguments describe each case, and make its description one sentence plus an
  example per kind.
- **Date:** 2026-11-06
- **Branch:** descriptions-leftovers

## Plan

Refusal sentences out of `extract_struct` (two), `change_type` (the primitive-use paragraph and
"is refused with the error"), `replace_declaration`, `function_to_method`, `replace_statements`,
`invert_if` and `undo`; `batch`'s "If a step is refused, nothing changes" said as what it does ("the
steps apply together or not at all"). `extract_struct`: one sentence and an example for the
parameter object, extract class and a record's components; `file`, `name`, `struct`,
`parameter`, `fields` and `field` worded for each case.

## Notes

- read: each description (`bin/jrefactor list`), the source lines of the nine passages.
- One batch of nine `replace_text` steps; no refusals; `verify` passed.
- Left on purpose: refusal words inside argument descriptions where they are the argument's
  meaning (`preserve_exceptions`, `names_only`, `batch`'s `retry`).

## Measured

`extract_struct` 2,321 characters to 2,198 (its description about 800; the rest is its ten
arguments), `change_type` 1,805 to 1,393, `replace_declaration` 1,318 to 972; all tools 44,400 to
43,200. Through jrefactor: one batch; by hand this record.

## Assessment

Through the tools. `extract_struct` stays above 1,700 because of its schema: three cases in one
argument set (the earlier question of one shape, `name` + `members` + `struct` + `as`, would
shrink it).

## Follow-ups

- One argument shape for `extract_struct` (and the same check for `create_file` and
  `add_declaration`), if the user wants it.
