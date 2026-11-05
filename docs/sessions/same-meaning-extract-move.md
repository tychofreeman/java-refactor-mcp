# NameCheck in extract_method and move_type (roadmap #77, last part)

- **Item:** the follow-up of `docs/sessions/same-meaning-check.md`, chosen by the
  user from "what's next?" (2026-11-04) over removing empty directories, more
  exceptional cases, and the A/B run.
- **Date:** 2026-11-04
- **Branch:** same-meaning-extract-move

## Plan

- `extract_method`: the selected statements are the fragments; the edits are the
  call line in their place and the new method after the member.
- `move_type`: the moved type is the fragment; the edit is the new file's text.
- A golden for each where a name would mean another declaration after the move,
  if one can be found that the tools' own rules do not already catch.

## Notes

- Probe for `move_type`: a nested `Cart` using its sibling `Line`, beside a
  top-level `Line` in the package. `move_type` already writes `Shop.Line`, so there
  is no capture to pin; for `extract_method` none was found either (the new method
  is in the same class, and the locals it uses become parameters of the same
  names). The check is the net under both; the goldens and a stress run show it
  refuses nothing that is safe.
- A false positive first: `extract_method` with several results writes
  `int x = bothResult.x();`, and the accessor `x()` is a name `x` that meant the
  local. A qualified name (`a.x`, `e.m()`, a method reference) resolves through its
  qualifier, so it cannot be captured; the check skips it now.
- Stress run on Commons Lang (seed 13, 200 calls): no failures and no refusal by
  the check (`extract_method` applied 10, `move_type` 12, to a package 7).
- `refusal:` the `replace_text` that called the new `qualified` before it existed
  was refused (the stub declared for it did not fit a static context); after
  `add_declaration` of `qualified`, the same `replace_text` applied.

## Measured

From `bin/session-report` (before the docs edits):

- tool calls: 11
- through jrefactor: 4 edit steps
- refused by jrefactor: 1 (above, answered by adding the method first)
- by hand: 2, neither Java of the project: the record (heredoc) and the probe
  project in the scratchpad (`printf`)
- large writes: 1 (the probe's files)

## Assessment

- **Through the tools or around them:** through.
- **Did the plan hold?** Yes, without the goldens it hoped for: no capture case
  exists that the tools' own rules let through.

## Follow-ups

- `rename`'s own scope checks and `NameCheck` could be compared; `rename` refuses
  hiding by a local today.
