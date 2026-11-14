# One comma-separated list insertion

- **Item:** issue #514 (code review of 2026-11-13, signatures #10), group 4 of tracking issue #532
- **Date:** 2026-11-14
- **Branch:** list-insert

## Plan

Pure consolidation. Add `SignatureChange.insertAt(list, open, at, item)`, next to `remove`, with
`open` the offset past the bracket (as `SignatureChange.insert` takes it), plus an overload for a
non-empty list (`TypeParameters` has no bracket offset at hand). Make `Signatures.insertAt` and
`TypeParameters.inserted` delegates and inline them; `insert` calls the helper. Unit test for the
helper; the add_parameter, extract_variable, type_switch and change_type goldens stay green.

## Notes

- read: `inspect` with `names` for the three copies and `pattern` for their callers.
- the copies differed only in the empty list's offset (`(` or past it) and `at > size`
  (`SignatureChange.insert` would have thrown; no caller passes it): no behavior to pin.
- tools: one batch (`add_declaration`, three `replace_declaration`), two `inline`s, one batch of
  two `replace_text`s for `(open - 1) + 1`, `create_file` for `SignatureChangeTest`.
- hand: docs only.

## Measured

Not measured: the session-log hook is not loaded here (the CLI log is).

## Assessment

Through the tools; delegate-then-inline did the callers' rewrite, `+ 1` included.

## Follow-ups

None.
