# inline_function writes an unused argument as a statement

- **Item:** the follow-up of `docs/sessions/inline-binds-arguments.md`: an argument
  with an effect whose parameter the body does not use was bound to an unused local
  (`int x = next();`). The user's request of 2026-11-05.
- **Date:** 2026-11-05
- **Branch:** inline-unused-argument

## Decisions (this session)

- Such an argument that can stand as a statement (a call, an assignment, `++`/`--`,
  a `new`) is written as one (`next();`); any other (`next() + 1`) keeps the local,
  since Java does not take it as a statement.

## Notes

- `inline_function_binds_dropped_effect`'s expected output changed to `next();`
  (red first); `inline_function_binds_unused_expression` pins the local for
  `next() + 1`.
- `hand:` none; `declarations` got the use counts by `add_parameter`.

## Measured

All Java through jrefactor (`add_parameter`, `replace_text`, `add_declaration`).
