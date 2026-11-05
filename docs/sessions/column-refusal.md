# Column refusals name the columns (roadmap #101)

- **Item:** roadmap #101, from the A/B run: three wrong columns for `p.bin()` in
  two runs (14 and 18 guessed, 16 tried; it starts at 14), a turn each. The user's
  request of 2026-11-05 ("Go ahead with 100 & 101").
- **Date:** 2026-11-05
- **Branch:** column-refusal

## Decisions (this session)

- **Name the columns, do not guess which was meant.** `extract_variable`'s wrong
  column now says "p.bin() starts at columns 14 and 23 of line 9, not 16. Pass one
  of them."; two occurrences without a column list theirs.
- **`inline_variable` had the same gap, worse:** a wrong column said "no variable
  named subtotal on line 6", which is false. Now it names the columns too.
  `rename` and `convert_lambda` already listed them.
- One helper, `Nodes.columns`, folded from the two copies (`inline_function`).

## Notes

- A session made the A/B's mistake while writing #174's goldens (columns 16 and
  25 for 14 and 23): the refusal would have said so.
- `hand:` none.

## Measured

All Java through jrefactor (`replace_text`, `add_declaration`,
`replace_declaration`, `inline_function`), goldens by `move_file` and
`create_file`; no refusals.
