# extract_struct renamed extract_type

- **Item:** the user's rename (2026-11-06): "struct" is Go's word; in Java the new type is a class
  or a record. Parity is kept where it makes sense; the name is meant for Go and TypeScript too.
  The options put to the user: keep `extract_struct` for parity, `extract_type`; the user's
  `introduce_params` was set aside as fitting only the method case.
- **Plan:** the published name, the `Def` and the class by `rename`; refusal text by
  `replace_text`; `extract_struct` an alias in `Tools.ALIASES`; goldens and probes renamed, one
  (`extract_type_old_names`) kept on the old tool and argument names.

## Notes

## Measured

Through jrefactor: one batch of 10 steps (8 `replace_text`, 2 `rename`), no refusals; by hand the
golden directory moves and `args.properties` (resources), the docs by script, this record.

## Assessment

Through the tools, as planned.

## Follow-ups

- gorefactor: the same rename for Go and TypeScript, with `extract_struct` an alias.
