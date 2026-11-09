# The bare-name field stub on the library-card A/B

- **Item:** the follow-up of `docs/sessions/field-stub-bare-name.md` (#288): rerun the skeleton
  arm of `eval-tasks/library-card.json` to see whether the four calls a run lost to bodies that
  use a field not yet declared are gone. The user said yes (2026-11-09).
- **Date:** 2026-11-09
- **Branch:** ab-card-bare-field

## Plan

Run gorefactor's harness on the skeleton server, Sonnet, `-arms mcp -runs 2`, as
`docs/sessions/ab-constructor-stub.md` did, and compare with its skeleton runs.

## Notes

- Results in `eval-runs/ab-card-bare-field` (not committed). $0.24 in all; both runs passed.

| | before (#287) | this run |
|---|---|---|
| cost a run | $0.13 | $0.12 |
| turns a run | 19.5 | 14.5 |
| input tokens a run | 323,360 | 241,200 |

- Run 1 sent the field and both bodies in one `add_declaration`, so it never needed the stub.
- Run 2 filled the two stub bodies first, as both earlier skeleton runs did, and both were
  refused: "borrowed cannot be resolved to a variable". The stub did not fire: `Card` had the
  method stub `borrowed()`, and `Stubs.Round.removed` counted a method of the same name as the
  field the edit removed. It then sent the field and the bodies again, one call each, and passed:
  two lost calls, not four (the bodies went one after another, not in parallel).
- Fix: `removed` takes `field` (through `add_parameter`, `false` at the five method calls), and
  matches fields only for a field and methods only for a method. Golden
  `stubs_field_bare_name_beside_method`, failing before.
- No rerun after the fix; it costs another $0.24 (ask first).

## Measured

From `bin/session-report`: the fix through jrefactor (one `add_parameter`, one batch of three
`replace_text`); by hand only the golden fixture (resources) and the docs below.

## Assessment

- The A/B found a bug every golden missed: the use case is a method and a field of one name,
  `borrowed()` and `borrowed`, which a getter of a counter always is. The goldens had the field
  alone.
- Turns fell by five a run, though only one run went the way the stub was built for.

## Follow-ups

- A rerun after the fix, to see run 2's path pass without a refusal.
