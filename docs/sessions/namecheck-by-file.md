# NameCheck keys a declaration's position by its file

- **Item:** the gap logged in #191: `NameCheck` recorded a declaration's position by
  offset alone, so a capture by a declaration at the same offset in another file
  would pass. The user's choice of 2026-11-05 ("Do items 1, 2, and 3").
- **Date:** 2026-11-05
- **Branch:** namecheck-by-file

## Decisions (this session)

- **The path comes from the parser:** `Jdt` sets each unit's file as a property
  (`Jdt.FILE`, `Jdt.fileOf`) in `parse` and `parseAll`, so `NameCheck.meanings`
  knows a fragment's file without its callers passing it. A position is
  `path@offset`; one from a unit parsed without a file (text alone) stays `@offset`,
  and `require` accepts that form as before, so nothing that passed is refused now.
- **A unit test, not a probe:** the collision needs a name whose meaning is by
  position (a local of an anonymous class) written into another file with a local of
  the name at the same offset; no tool moves such a name across files, so
  `NameCheckTest` builds it directly. Red first (accepted), then refused.

## Notes

- `refusal:` my first batch used a pattern variable outside the `?:` that bound it;
  resent with a cast.
- `hand:` none.

## Measured

All Java through jrefactor (`replace_text`, `add_declaration`, `replace_declaration`,
`create_file` of the test).
