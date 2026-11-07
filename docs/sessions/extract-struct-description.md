# extract_struct: the method case without its parenthetical

- **Item:** the user's note that "(a parameter object)" in `extract_struct`'s description was
  unnecessary and unlike the other two cases (2026-11-06).
- **Plan:** remove it with `replace_text`; the contract keeps the term, where it explains.

## Notes

## Measured

Through jrefactor: one `replace_text`; by hand this record.

## Assessment

Through the tools.

## Follow-ups

- Whether to rename `extract_struct` (the user asked about `introduce_params`): put to the user.
